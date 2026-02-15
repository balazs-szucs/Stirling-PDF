/*
package stirling.software.SPDF.config;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;

@AutomaticFeature
public class StirlingPdfFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // Register AWT substitutions and ensuring they are initialized at runtime
        // This is critical for PDF rendering on Linux/Docker
        RuntimeClassInitialization.initializeAtRunTime(
            "sun.awt.X11GraphicsEnvironment",
            "sun.java2d.xr.XRSurfaceData"
        );
    }
}
*/
