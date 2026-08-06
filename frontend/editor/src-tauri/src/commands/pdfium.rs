use pdfium_render::prelude::*;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};
use tauri::{AppHandle, Manager};

static PDFIUM: OnceLock<Pdfium> = OnceLock::new();
static NEXT_DOC_HANDLE: AtomicU64 = AtomicU64::new(1);
static DOCUMENTS: LazyDocuments = LazyDocuments::new();

struct NativeDocument(PdfDocument<'static>);
unsafe impl Send for NativeDocument {}
unsafe impl Sync for NativeDocument {}

struct LazyDocuments {
    map: Mutex<Option<HashMap<u64, NativeDocument>>>,
}

impl LazyDocuments {
    const fn new() -> Self {
        Self {
            map: Mutex::new(None),
        }
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, Option<HashMap<u64, NativeDocument>>> {
        self.map.lock().unwrap()
    }
}

/// Locate and bind the native PDFium shared library from app resources.
/// Hard fails if bundled library is not present (strictly no system fallback).
pub fn get_pdfium(app: &AppHandle) -> Result<&'static Pdfium, String> {
    if let Some(pdfium_instance) = PDFIUM.get() {
        return Ok(pdfium_instance);
    }

    let lib_name = Pdfium::pdfium_platform_library_name_at_path("./");
    let mut search_paths: Vec<PathBuf> = Vec::new();

    if let Ok(res_dir) = app.path().resource_dir() {
        search_paths.push(res_dir.join("libs").join(&lib_name));
        search_paths.push(res_dir.join(&lib_name));
    }

    if let Ok(current_dir) = std::env::current_dir() {
        search_paths.push(
            current_dir
                .join("frontend")
                .join("editor")
                .join("src-tauri")
                .join("libs")
                .join(&lib_name),
        );
        search_paths.push(current_dir.join("src-tauri").join("libs").join(&lib_name));
    }

    let temp_target_dir = std::env::temp_dir().join("stirling_pdfium_natives");
    search_paths.push(temp_target_dir.join(&lib_name));

    let mut bound_library = None;
    for path in &search_paths {
        if path.exists() {
            match Pdfium::bind_to_library(path) {
                Ok(bindings) => {
                    log::info!("Native PDFium binary loaded from {:?}", path);
                    bound_library = Some(bindings);
                    break;
                }
                Err(err) => {
                    log::warn!("Failed to bind native PDFium at {:?}: {}", path, err);
                }
            }
        }
    }

    let bindings = match bound_library {
        Some(b) => b,
        None => {
            return Err(format!(
                "Native PDFium shared library ({}) not found in application resources",
                lib_name.display()
            ));
        }
    };

    let pdfium = Pdfium::new(bindings);
    let _ = PDFIUM.set(pdfium);

    PDFIUM
        .get()
        .ok_or_else(|| "Failed to initialize native PDFium library".to_string())
}

#[tauri::command]
pub async fn pdfium_native_open_document(
    app: AppHandle,
    file_bytes: Vec<u8>,
    password: Option<String>,
) -> Result<u64, String> {
    let pdfium = get_pdfium(&app)?;
    let pass_ref = password.as_deref();

    let doc = pdfium
        .load_pdf_from_byte_vec(file_bytes, pass_ref)
        .map_err(|e| format!("Failed to open PDF document: {}", e))?;

    let handle = NEXT_DOC_HANDLE.fetch_add(1, Ordering::SeqCst);
    let mut guard = DOCUMENTS.lock();
    let map = guard.get_or_insert_with(HashMap::new);
    map.insert(handle, NativeDocument(doc));

    log::debug!("Opened native PDF document handle #{}", handle);
    Ok(handle)
}

#[tauri::command]
pub async fn pdfium_native_get_page_count(
    app: AppHandle,
    doc_handle: u64,
) -> Result<u32, String> {
    let _ = get_pdfium(&app)?;
    let guard = DOCUMENTS.lock();
    let map = guard.as_ref().ok_or("No active documents")?;
    let native_doc = map
        .get(&doc_handle)
        .ok_or_else(|| format!("Invalid document handle: {}", doc_handle))?;

    Ok(native_doc.0.pages().len() as u32)
}

#[tauri::command]
pub async fn pdfium_native_render_page(
    app: AppHandle,
    doc_handle: u64,
    page_index: u32,
    scale: Option<f32>,
) -> Result<Vec<u8>, String> {
    let _ = get_pdfium(&app)?;
    let scale_val = scale.unwrap_or(1.0).max(0.1);

    if page_index > u16::MAX as u32 {
        return Err(format!("Page index {} exceeds PDFium maximum limit", page_index));
    }

    let (width, height, raw_pixels) = {
        let guard = DOCUMENTS.lock();
        let map = guard.as_ref().ok_or("No active documents")?;
        let native_doc = map
            .get(&doc_handle)
            .ok_or_else(|| format!("Invalid document handle: {}", doc_handle))?;

        let pages = native_doc.0.pages();
        let page_count = pages.len() as u32;

        if page_index >= page_count {
            return Err(format!(
                "Page index out of bounds: {} >= {}",
                page_index, page_count
            ));
        }

        let page = pages
            .get((page_index as u16).into())

            .map_err(|e| format!("Failed to load page {}: {}", page_index, e))?;

        let render_width = (page.width().value * scale_val) as i32;
        let render_config = PdfRenderConfig::new().set_target_width(render_width);

        let bitmap = page
            .render_with_config(&render_config)
            .map_err(|e| format!("Failed to render page bitmap: {}", e))?;

        let image = bitmap
            .as_image()
            .map_err(|e| format!("Failed to convert bitmap to image: {}", e))?;
        let rgba = image.to_rgba8();
        (rgba.width(), rgba.height(), rgba.into_raw())
    };

    let mut response = Vec::with_capacity(8 + raw_pixels.len());
    response.extend_from_slice(&width.to_be_bytes());
    response.extend_from_slice(&height.to_be_bytes());
    response.extend_from_slice(&raw_pixels);

    Ok(response)
}

#[tauri::command]
pub async fn pdfium_native_extract_text(
    app: AppHandle,
    doc_handle: u64,
    page_index: u32,
) -> Result<String, String> {
    let _ = get_pdfium(&app)?;

    if page_index > u16::MAX as u32 {

        return Err(format!("Page index {} exceeds PDFium maximum limit", page_index));
    }

    let guard = DOCUMENTS.lock();
    let map = guard.as_ref().ok_or("No active documents")?;
    let native_doc = map
        .get(&doc_handle)
        .ok_or_else(|| format!("Invalid document handle: {}", doc_handle))?;

    let pages = native_doc.0.pages();
    let page_count = pages.len() as u32;

    if page_index >= page_count {
        return Err(format!(
            "Page index out of bounds: {} >= {}",
            page_index, page_count
        ));
    }

    let page = pages
        .get((page_index as u16).into())

        .map_err(|e| format!("Failed to load page {}: {}", page_index, e))?;

    let text_page = page
        .text()
        .map_err(|e| format!("Failed to extract text: {}", e))?;

    Ok(text_page.all())
}

#[tauri::command]
pub async fn pdfium_native_close_document(
    app: AppHandle,
    doc_handle: u64,
) -> Result<(), String> {
    let _ = get_pdfium(&app)?;
    let mut guard = DOCUMENTS.lock();
    if let Some(map) = guard.as_mut() {
        if map.remove(&doc_handle).is_some() {
            log::debug!("Closed native PDF document handle #{}", doc_handle);
        }
    }
    Ok(())
}
