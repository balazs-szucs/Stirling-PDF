use std::fs;
use std::path::PathBuf;
use tauri::AppHandle;

#[tauri::command]
pub async fn print_pdf_file_native(
    app: AppHandle,
    file_path: Option<String>,
    pdf_bytes: Option<Vec<u8>>,
    title: Option<String>,
) -> Result<(), String> {
    let mut temp_file_path: Option<PathBuf> = None;
    let target_path: String = match (file_path, pdf_bytes) {
        (Some(path), _) => path,
        (None, Some(bytes)) => {
            let temp_dir = std::env::temp_dir();
            let file_name = format!("stirling_print_{}.pdf", std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis());
            let path = temp_dir.join(file_name);
            fs::write(&path, bytes).map_err(|e| format!("Failed to write temp print file: {}", e))?;
            let path_str = path.to_string_lossy().to_string();
            temp_file_path = Some(path);
            path_str
        }
        (None, None) => return Err("No file path or PDF bytes provided for printing".to_string()),
    };

    let result = print_pdf_path_internal(app, &target_path, title);

    if let Some(temp_path) = temp_file_path {
        let _ = fs::remove_file(temp_path);
    }

    result
}

#[cfg(target_os = "macos")]
fn print_pdf_path_internal(app: AppHandle, file_path: &str, title: Option<String>) -> Result<(), String> {
    use std::path::Path;
    use std::sync::mpsc;
    use objc2::rc::autoreleasepool;
    use objc2::AnyThread;
    use objc2_app_kit::NSPrintInfo;
    use objc2_foundation::{MainThreadMarker, NSString, NSURL};
    use objc2_pdf_kit::{PDFDocument, PDFPrintScalingMode};

    if !Path::new(file_path).exists() {
        return Err(format!("Print file does not exist: {}", file_path));
    }

    let (sender, receiver) = mpsc::channel();
    let path_clone = file_path.to_string();

    app.run_on_main_thread(move || {
        let result = autoreleasepool(|_| {
            let mtm = MainThreadMarker::new()
                .ok_or_else(|| "macOS print must run on the main thread".to_string())?;

            let path_string = NSString::from_str(&path_clone);
            let file_url = NSURL::fileURLWithPath(&path_string);
            let document = unsafe { PDFDocument::initWithURL(PDFDocument::alloc(), &file_url) }
                .ok_or_else(|| format!("Failed to load PDF for printing: {}", path_clone))?;

            let print_info = NSPrintInfo::sharedPrintInfo();
            let print_operation = unsafe {
                document
                    .printOperationForPrintInfo_scalingMode_autoRotate(
                        Some(&print_info),
                        PDFPrintScalingMode::PageScaleDownToFit,
                        true,
                        mtm,
                    )
            }
            .ok_or_else(|| "PDFKit did not create a print operation".to_string())?;

            if let Some(job_title) = title.as_deref() {
                print_operation.setJobTitle(Some(&NSString::from_str(job_title)));
            }

            print_operation.setShowsPrintPanel(true);
            print_operation.setShowsProgressPanel(true);
            let _ = print_operation.runOperation();
            Ok(())
        });

        let _ = sender.send(result);
    }).map_err(|error| error.to_string())?;

    receiver
        .recv()
        .map_err(|error| error.to_string())?
}

#[cfg(target_os = "windows")]
fn print_pdf_path_internal(_app: AppHandle, file_path: &str, _title: Option<String>) -> Result<(), String> {
    use std::ffi::OsStr;
    use std::os::windows::ffi::OsStrExt;
    use windows::core::PCWSTR;
    use windows::Win32::UI::Shell::ShellExecuteW;
    use windows::Win32::UI::WindowsAndMessaging::SW_HIDE;

    let operation: Vec<u16> = OsStr::new("print").encode_wide().chain(std::iter::once(0)).collect();
    let file: Vec<u16> = OsStr::new(file_path).encode_wide().chain(std::iter::once(0)).collect();

    let result = unsafe {
        ShellExecuteW(
            None,
            PCWSTR(operation.as_ptr()),
            PCWSTR(file.as_ptr()),
            PCWSTR(std::ptr::null()),
            PCWSTR(std::ptr::null()),
            SW_HIDE,
        )
    };

    if (result.0 as usize) > 32 {
        Ok(())
    } else {
        Err(format!("Windows native ShellExecute print failed with code: {:?}", result.0))
    }
}

#[cfg(not(any(target_os = "macos", target_os = "windows")))]
fn print_pdf_path_internal(_app: AppHandle, file_path: &str, _title: Option<String>) -> Result<(), String> {
    use std::process::Command;

    let status = Command::new("lpr")
        .arg(file_path)
        .status()
        .map_err(|e| format!("Failed to execute lpr command: {}", e))?;

    if status.success() {
        Ok(())
    } else {
        Err(format!("lpr print command returned non-zero exit code: {:?}", status.code()))
    }
}
