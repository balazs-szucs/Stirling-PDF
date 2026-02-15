package stirling.software.SPDF.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers runtime hints required for GraalVM native image compilation. This class ensures that
 * all reflection, resource, proxy, and JNI metadata is properly registered for the native image
 * build so that dynamic features work correctly at runtime.
 *
 * <p>This covers:
 *
 * <ul>
 *   <li>PDFBox / FontBox classes that use reflection
 *   <li>BouncyCastle security provider
 *   <li>Jackson serialization classes
 *   <li>Java AWT/2D classes for PDF rendering
 *   <li>XML parsing factories (DocumentBuilderFactory, TransformerFactory)
 *   <li>ResourceBundle for i18n
 *   <li>JMX MXBean for resource monitoring
 *   <li>Application-specific classes using reflection
 * </ul>
 */
public class NativeImageRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerPdfBoxHints(hints);
        registerBouncyCastleHints(hints);
        registerAwtHints(hints);
        registerXmlHints(hints);
        registerJaxbProxyHints(hints);
        registerResourceHints(hints);
        registerApplicationHints(hints);
        registerJmxHints(hints);
        registerSpringAopHints(hints);
        registerSpringDocHints(hints);
    }

    private void registerPdfBoxHints(RuntimeHints hints) {
        // PDFBox core classes
        registerReflection(
                hints,
                "org.apache.pdfbox.pdmodel.PDDocument",
                "org.apache.pdfbox.pdmodel.PDPage",
                "org.apache.pdfbox.pdmodel.PDPageContentStream",
                "org.apache.pdfbox.pdmodel.common.PDRectangle",
                "org.apache.pdfbox.pdmodel.font.PDType1Font",
                "org.apache.pdfbox.pdmodel.font.PDFont",
                "org.apache.pdfbox.pdmodel.font.PDTrueTypeFont",
                "org.apache.pdfbox.pdmodel.font.PDType0Font",
                "org.apache.pdfbox.pdmodel.encryption.StandardSecurityHandler",
                "org.apache.pdfbox.rendering.PDFRenderer",
                "org.apache.pdfbox.multipdf.PDFMergerUtility",
                "org.apache.pdfbox.cos.COSName",
                "org.apache.pdfbox.io.RandomAccessReadBuffer",
                "org.apache.pdfbox.io.RandomAccessReadBufferedFile",
                "org.apache.pdfbox.preflight.PreflightDocument");

        // FontBox classes
        registerReflection(
                hints,
                "org.apache.fontbox.ttf.TTFParser",
                "org.apache.fontbox.ttf.TrueTypeFont",
                "org.apache.fontbox.ttf.BufferedRandomAccessFile",
                "org.apache.fontbox.cff.CFFParser");

        // PDFBox resources
        hints.resources().registerPattern("org/apache/pdfbox/resources/**");
        hints.resources().registerPattern("org/apache/fontbox/**");
        hints.resources().registerPattern("org/apache/pdfbox/resources/glyphlist/*.txt");
        hints.resources().registerPattern("org/apache/pdfbox/resources/afm/*.afm");
        hints.resources().registerPattern("org/apache/pdfbox/resources/cmap/*");
        hints.resources().registerPattern("org/apache/pdfbox/resources/icc/*.icc");
    }

    private void registerBouncyCastleHints(RuntimeHints hints) {
        registerReflection(hints, "org.bouncycastle.jce.provider.BouncyCastleProvider");

        // Register as security provider at build time
        hints.reflection().registerType(
            org.springframework.aot.hint.TypeReference.of("org.bouncycastle.jce.provider.BouncyCastleProvider"),
            hint -> hint.withMembers(
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )
        );
    }

    private void registerAwtHints(RuntimeHints hints) {
        // Core AWT classes for PDF rendering
        registerReflection(
                hints,
                "java.awt.Font",
                "java.awt.Color",
                "java.awt.image.BufferedImage",
                "java.awt.Graphics2D",
                "java.awt.GraphicsEnvironment",
                "java.awt.Toolkit",
                "java.awt.image.ColorModel",
                "java.awt.image.Raster",
                "java.awt.image.WritableRaster",
                "java.awt.image.SampleModel",
                "java.awt.image.DataBuffer",
                "java.awt.image.DataBufferInt",
                "java.awt.image.DataBufferByte",
                "java.awt.image.IndexColorModel",
                "java.awt.image.DirectColorModel",
                "java.awt.image.ComponentColorModel",
                "java.awt.image.SinglePixelPackedSampleModel",
                "java.awt.color.ColorSpace",
                "java.awt.color.ICC_ColorSpace",
                "java.awt.color.ICC_Profile",
                "java.awt.geom.AffineTransform",
                "java.awt.geom.Path2D",
                "java.awt.geom.Path2D$Float",
                "java.awt.geom.GeneralPath",
                "java.awt.geom.Rectangle2D",
                "java.awt.geom.Rectangle2D$Float",
                "java.awt.Rectangle",
                "java.awt.BasicStroke",
                "java.awt.AlphaComposite",
                "java.awt.RenderingHints",
                "java.io.RandomAccessFile");

        // Register sun.* internal AWT/Java2D classes needed for JNI and rendering
        registerReflectionSafe(
                hints,
                "sun.java2d.HeadlessGraphicsEnvironment",
                "sun.awt.X11GraphicsEnvironment",
                "sun.awt.SunGraphicsEnvironment",
                "sun.awt.PlatformGraphicsInfo",
                "sun.java2d.SunGraphics2D",
                "sun.java2d.SurfaceData",
                "sun.java2d.NullSurfaceData",
                "sun.java2d.Disposer",
                "sun.awt.image.IntegerComponentRaster",
                "sun.awt.image.ByteComponentRaster",
                "sun.awt.image.BytePackedRaster",
                "sun.awt.image.ShortComponentRaster",
                "sun.awt.image.BufImgSurfaceData",
                "sun.awt.image.SunVolatileImage",
                "sun.awt.SunHints",
                "sun.java2d.loops.GraphicsPrimitive",
                "sun.java2d.loops.GraphicsPrimitiveMgr",
                "sun.java2d.loops.SurfaceType",
                "sun.java2d.loops.CompositeType",
                "sun.java2d.loops.Blit",
                "sun.java2d.loops.BlitBg",
                "sun.java2d.loops.ScaledBlit",
                "sun.java2d.loops.FillRect",
                "sun.java2d.loops.FillSpans",
                "sun.java2d.loops.DrawLine",
                "sun.java2d.loops.DrawRect",
                "sun.java2d.loops.DrawPolygons",
                "sun.java2d.loops.DrawPath",
                "sun.java2d.loops.FillPath",
                "sun.java2d.loops.MaskBlit",
                "sun.java2d.loops.MaskFill",
                "sun.java2d.loops.DrawGlyphList",
                "sun.java2d.loops.DrawGlyphListAA",
                "sun.java2d.loops.DrawGlyphListLCD",
                "sun.java2d.loops.TransformHelper",
                "sun.java2d.loops.XORComposite",
                // Additional base primitive types (in jni-config but missing from reflection)
                "sun.java2d.loops.DrawGlyphListColor",
                "sun.java2d.loops.TransformBlit",
                "sun.java2d.loops.FillParallelogram",
                "sun.java2d.loops.DrawParallelogram",
                // *ANY implementation classes loaded by GraphicsPrimitiveProxy.instantiate()
                "sun.java2d.loops.SetDrawLineANY",
                "sun.java2d.loops.SetFillRectANY",
                "sun.java2d.loops.SetFillSpansANY",
                "sun.java2d.loops.SetDrawRectANY",
                "sun.java2d.loops.SetDrawPolygonsANY",
                "sun.java2d.loops.SetDrawPathANY",
                "sun.java2d.loops.SetFillPathANY",
                "sun.java2d.loops.SetDrawGlyphListANY",
                "sun.java2d.loops.SetDrawGlyphListAAANY",
                "sun.java2d.loops.SetDrawGlyphListLCDANY",
                "sun.java2d.loops.SetDrawGlyphListColorANY",
                "sun.java2d.loops.SetMaskFillANY",
                "sun.java2d.loops.SetFillParallelogramANY",
                "sun.java2d.loops.SetDrawParallelogramANY",
                "sun.java2d.loops.TransformHelperANY",
                "sun.java2d.loops.IsomorphicBlit",
                "sun.java2d.loops.AnyBlit",
                "sun.java2d.loops.GeneralMaskBlit",
                "sun.java2d.pipe.Region",
                "sun.java2d.pipe.RegionIterator",
                "sun.java2d.pipe.SpanClipRenderer",
                "sun.font.Font2D",
                "sun.font.PhysicalFont",
                "sun.font.TrueTypeFont",
                "sun.font.Type1Font",
                "sun.font.SunFontManager",
                "sun.font.StrikeCache",
                "sun.font.GlyphList",
                "sun.font.FontConfigManager",
                "sun.font.FontConfigManager$FcCompFont",
                "sun.font.FontConfigManager$FontConfigFont",
                "sun.font.FontManagerForSGE",
                "sun.font.FreetypeFontScaler",
                "sun.font.FontStrike",
                "sun.font.PhysicalStrike",
                "sun.font.GlyphLayout");

        // Register sun.java2d classes needed for PDF rendering (JNI)
        try {
            hints.reflection()
                    .registerType(
                            Class.forName("sun.java2d.pipe.ShapeSpanIterator"),
                            MemberCategory.ACCESS_DECLARED_FIELDS,
                            MemberCategory.INVOKE_DECLARED_METHODS);
        } catch (ClassNotFoundException e) {
            // Not available on this JDK, skip
        }

        // Register JNI hints for AWT native library classes
        // System.load is needed because AWT's JNI_OnLoad calls GetStaticMethodID
        // for System.load to load dependent native libraries
        registerJniHints(
                hints,
                "java.lang.System",
                "java.lang.ClassLoader",
                "java.lang.Runtime",
                "java.awt.GraphicsEnvironment",
                "java.awt.image.ColorModel",
                "java.awt.image.Raster",
                "java.awt.image.SampleModel",
                "java.awt.image.BufferedImage",
                "java.awt.image.DataBuffer",
                "java.awt.image.DataBufferInt",
                "java.awt.image.DataBufferByte",
                "java.awt.color.ColorSpace",
                "java.awt.color.ICC_Profile",
                "java.awt.geom.AffineTransform",
                "java.awt.Rectangle",
                "java.awt.Point",
                "java.awt.Dimension",
                "java.awt.Insets",
                "java.awt.Color",
                "java.awt.Font");

        registerJniHintsSafe(
                hints,
                "sun.awt.SunGraphicsEnvironment",
                "sun.java2d.HeadlessGraphicsEnvironment",
                "sun.awt.PlatformGraphicsInfo",
                "sun.awt.image.IntegerComponentRaster",
                "sun.awt.image.ByteComponentRaster",
                "sun.awt.image.BytePackedRaster",
                "sun.awt.image.ShortComponentRaster",
                "sun.awt.image.BufImgSurfaceData",
                "sun.java2d.SunGraphics2D",
                "sun.java2d.SurfaceData",
                "sun.java2d.Disposer",
                "sun.java2d.pipe.Region",
                "sun.java2d.pipe.RegionIterator",
                "sun.java2d.pipe.SpanClipRenderer",
                "sun.java2d.pipe.ShapeSpanIterator",
                "sun.java2d.loops.GraphicsPrimitive",
                "sun.java2d.loops.GraphicsPrimitiveMgr",
                "sun.java2d.loops.SurfaceType",
                "sun.java2d.loops.CompositeType",
                "sun.font.StrikeCache",
                "sun.font.GlyphList",
                "sun.font.FreetypeFontScaler",
                "sun.font.Font2D",
                "sun.font.PhysicalStrike",
                "sun.font.FontConfigManager$FcCompFont",
                "sun.font.FontConfigManager$FontConfigFont");
    }

    private void registerXmlHints(RuntimeHints hints) {
        registerReflection(
                hints,
                "javax.xml.parsers.DocumentBuilderFactory",
                "javax.xml.transform.TransformerFactory",
                "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl",
                "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");

        // JAXB annotation classes (needed for VeraPDF XML deserialization in native image)
        registerReflectionSafe(
                hints,
                "jakarta.xml.bind.annotation.XmlRootElement",
                "jakarta.xml.bind.annotation.XmlElement",
                "jakarta.xml.bind.annotation.XmlAttribute",
                "jakarta.xml.bind.annotation.XmlType",
                "jakarta.xml.bind.annotation.XmlAccessorType",
                "jakarta.xml.bind.annotation.XmlEnum",
                "jakarta.xml.bind.annotation.XmlEnumValue",
                "jakarta.xml.bind.annotation.XmlElementWrapper",
                "jakarta.xml.bind.annotation.XmlValue",
                "jakarta.xml.bind.annotation.XmlTransient",
                "jakarta.xml.bind.annotation.XmlList",
                "jakarta.xml.bind.annotation.XmlSeeAlso",
                "jakarta.xml.bind.annotation.XmlElementRef",
                "jakarta.xml.bind.annotation.XmlElementRefs",
                "jakarta.xml.bind.annotation.XmlElements",
                "jakarta.xml.bind.annotation.XmlSchemaType",
                "jakarta.xml.bind.annotation.XmlAccessorOrder",
                "jakarta.xml.bind.annotation.XmlAnyElement",
                "jakarta.xml.bind.annotation.XmlAnyAttribute",
                "jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter",
                "jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapters",
                "jakarta.xml.bind.JAXBContext",
                // JAXB RI context factory
                "com.sun.xml.bind.v2.ContextFactory",
                "org.glassfish.jaxb.runtime.v2.ContextFactory");
    }

    /**
     * Register dynamic proxy hints for JAXB annotation proxies. VeraPDF's JAXB runtime
     * (com.sun.xml.bind.v2.model.annotation.LocatableAnnotation) creates dynamic proxies that
     * implement [AnnotationType, Locatable] for every JAXB annotation it encounters during
     * XML deserialization. Each proxy combination must be pre-registered for native images.
     */
    private void registerJaxbProxyHints(RuntimeHints hints) {
        String locatable = "com.sun.xml.bind.v2.model.annotation.Locatable";

        // All JAXB annotations that VeraPDF's JAXB RI wraps with LocatableAnnotation proxies
        String[] jaxbAnnotations = {
                "javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter",
                "javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters",
                "javax.xml.bind.annotation.XmlRootElement",
                "javax.xml.bind.annotation.XmlElement",
                "javax.xml.bind.annotation.XmlAttribute",
                "javax.xml.bind.annotation.XmlType",
                "javax.xml.bind.annotation.XmlAccessorType",
                "javax.xml.bind.annotation.XmlEnum",
                "javax.xml.bind.annotation.XmlEnumValue",
                "javax.xml.bind.annotation.XmlElementWrapper",
                "javax.xml.bind.annotation.XmlValue",
                "javax.xml.bind.annotation.XmlTransient",
                "javax.xml.bind.annotation.XmlList",
                "javax.xml.bind.annotation.XmlSeeAlso",
                "javax.xml.bind.annotation.XmlElementRef",
                "javax.xml.bind.annotation.XmlElementRefs",
                "javax.xml.bind.annotation.XmlElements",
                "javax.xml.bind.annotation.XmlSchemaType",
                "javax.xml.bind.annotation.XmlAccessorOrder",
                "javax.xml.bind.annotation.XmlAnyElement",
                "javax.xml.bind.annotation.XmlAnyAttribute",
                "javax.xml.bind.annotation.XmlSchema",
                "javax.xml.bind.annotation.XmlNs",
                "javax.xml.bind.annotation.XmlMixed",
                "javax.xml.bind.annotation.XmlID",
                "javax.xml.bind.annotation.XmlIDREF",
                "javax.xml.bind.annotation.XmlAttachmentRef",
                "javax.xml.bind.annotation.XmlMimeType",
                "javax.xml.bind.annotation.XmlInlineBinaryData"
        };

        for (String annotation : jaxbAnnotations) {
            try {
                Class<?> annotationClass =
                        Class.forName(annotation, false, getClass().getClassLoader());
                Class<?> locatableClass =
                        Class.forName(locatable, false, getClass().getClassLoader());
                hints.proxies().registerJdkProxy(annotationClass, locatableClass);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // Annotation or Locatable not on classpath, skip
            }
        }
    }

    private void registerResourceHints(RuntimeHints hints) {
        // Static resources
        hints.resources().registerPattern("static/**");
        hints.resources().registerPattern("templates/**");
        hints.resources().registerPattern("*.properties");
        hints.resources().registerPattern("*.yml");
        hints.resources().registerPattern("*.yaml");
        hints.resources().registerPattern("*.html");
        hints.resources().registerPattern("*.json");
        hints.resources().registerPattern("*.toml");
        hints.resources().registerPattern("*.css");
        hints.resources().registerPattern("*.js");
        hints.resources().registerPattern("*.mjs");
        hints.resources().registerPattern("*.svg");
        hints.resources().registerPattern("*.png");
        hints.resources().registerPattern("*.ico");
        hints.resources().registerPattern("*.ttf");
        hints.resources().registerPattern("*.otf");
        hints.resources().registerPattern("version.properties");
        hints.resources().registerPattern("META-INF/**");

        // JDK ICC color profiles (required for CMYK/sRGB color conversion in PDFBox)
        hints.resources().registerPattern("sun/java2d/cmm/profiles/*.pf");

        // Application-bundled ICC color profiles (used by ConvertPDFToPDFA)
        hints.resources().registerPattern("icc/*.icc");
        hints.resources().registerPattern("icc/**");

        // Type3 font library resources (fonts, index, signatures)
        hints.resources().registerPattern("type3/**");

        // VeraPDF validation profiles and schemas
        hints.resources().registerPattern("org/verapdf/pdfa/validation/*");
        hints.resources().registerPattern("org/verapdf/metadata/fixer/*");
        hints.resources().registerPattern("org/verapdf/release/*");
        hints.resources().registerPattern("org/verapdf/**/*.xml");
        hints.resources().registerPattern("org/verapdf/**/*.xsd");

        // Resource bundles for i18n
        hints.resources().registerResourceBundle("messages");
    }

    private void registerApplicationHints(RuntimeHints hints) {
        // Classes probed via Class.forName
        registerReflectionSafe(
                hints,
                "stirling.software.proprietary.security.configuration.SecurityConfiguration");

        // Classes used via reflection in JobExecutorService
        registerReflectionSafe(hints, "stirling.software.common.service.JobExecutorService");

        // ZXing barcode classes
        registerReflection(
                hints,
                "com.google.zxing.MultiFormatReader",
                "com.google.zxing.MultiFormatWriter");

        // VeraPDF
        registerReflectionSafe(hints, "org.verapdf.pdfa.flavours.PDFAFlavour");

        // VeraPDF JAXB-annotated implementation classes (for XML profile deserialization)
        registerReflectionSafe(
                hints,
                // Flavour nested enums
                "org.verapdf.pdfa.flavours.PDFAFlavour$Specification",
                "org.verapdf.pdfa.flavours.PDFAFlavour$SpecificationFamily",
                "org.verapdf.pdfa.flavours.PDFAFlavour$Level",
                "org.verapdf.pdfa.flavours.PDFAFlavour$Part",
                // Validation profile classes
                "org.verapdf.pdfa.validation.profiles.ValidationProfileImpl",
                "org.verapdf.pdfa.validation.profiles.ValidationProfileImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.ProfileDetailsImpl",
                "org.verapdf.pdfa.validation.profiles.ProfileDetailsImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.RuleImpl",
                "org.verapdf.pdfa.validation.profiles.RuleImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.RuleIdImpl",
                "org.verapdf.pdfa.validation.profiles.RuleIdImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.VariableImpl",
                "org.verapdf.pdfa.validation.profiles.VariableImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.ReferenceImpl",
                "org.verapdf.pdfa.validation.profiles.ReferenceImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.ErrorDetailsImpl",
                "org.verapdf.pdfa.validation.profiles.ErrorDetailsImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.ErrorArgumentImpl",
                "org.verapdf.pdfa.validation.profiles.ErrorArgumentImpl$Adapter",
                "org.verapdf.pdfa.validation.profiles.ProfileDirectoryImpl",
                "org.verapdf.pdfa.validation.profiles.Profiles",
                "org.verapdf.pdfa.validation.validators.ValidatorConfigImpl",
                // Validation result classes
                "org.verapdf.pdfa.results.ValidationResultImpl",
                "org.verapdf.pdfa.results.ValidationResultImpl$Adapter",
                "org.verapdf.pdfa.results.TestAssertionImpl",
                "org.verapdf.pdfa.results.TestAssertionImpl$Adapter",
                "org.verapdf.pdfa.results.LocationImpl",
                "org.verapdf.pdfa.results.LocationImpl$Adapter",
                "org.verapdf.pdfa.results.MetadataFixerResultImpl",
                // Foundry classes
                "org.verapdf.pdfa.AbstractFoundry",
                "org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider",
                // Component/Processor classes
                "org.verapdf.component.AuditDurationImpl",
                "org.verapdf.component.ComponentDetailsImpl",
                "org.verapdf.component.LogImpl",
                "org.verapdf.component.LogsSummaryImpl",
                "org.verapdf.processor.ProcessorConfigImpl",
                "org.verapdf.processor.ProcessorResultImpl",
                "org.verapdf.processor.TaskResultImpl",
                "org.verapdf.features.FeatureExtractorConfigImpl",
                "org.verapdf.metadata.fixer.FixerConfigImpl",
                "org.verapdf.ReleaseDetails",
                // Report classes
                "org.verapdf.processor.reports.ValidationReportImpl",
                "org.verapdf.processor.reports.CheckImpl",
                "org.verapdf.processor.reports.RuleSummaryImpl",
                "org.verapdf.processor.reports.ValidationBatchSummaryImpl",
                "org.verapdf.processor.reports.BatchSummaryImpl",
                "org.verapdf.processor.reports.ValidationDetailsImpl",
                "org.verapdf.processor.reports.ItemDetails",
                // JAXB serialiser
                "org.verapdf.core.XmlSerialiser");

        // JAXB runtime property/bean classes (used internally by JAXB for VeraPDF deserialization)
        registerReflectionSafe(
                hints,
                "com.sun.xml.bind.v2.runtime.property.ArrayElementProperty",
                "com.sun.xml.bind.v2.runtime.property.ArrayElementNodeProperty",
                "com.sun.xml.bind.v2.runtime.property.ArrayElementLeafProperty",
                "com.sun.xml.bind.v2.runtime.property.SingleElementNodeProperty",
                "com.sun.xml.bind.v2.runtime.property.SingleElementLeafProperty",
                "com.sun.xml.bind.v2.runtime.property.ArrayReferenceNodeProperty",
                "com.sun.xml.bind.v2.runtime.property.SingleReferenceNodeProperty",
                "com.sun.xml.bind.v2.runtime.property.SingleMapNodeProperty",
                "com.sun.xml.bind.v2.runtime.property.AttributeProperty",
                "com.sun.xml.bind.v2.runtime.property.ValueProperty",
                "com.sun.xml.bind.v2.runtime.ClassBeanInfoImpl",
                "com.sun.xml.bind.v2.runtime.ElementBeanInfoImpl",
                "com.sun.xml.bind.v2.runtime.LeafBeanInfoImpl",
                "com.sun.xml.bind.v2.runtime.ArrayBeanInfoImpl",
                "com.sun.xml.bind.v2.model.impl.RuntimeModelBuilder",
                "com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl",
                "com.sun.xml.bind.v2.model.impl.RuntimeElementInfoImpl",
                "com.sun.xml.bind.v2.model.impl.RuntimeEnumLeafInfoImpl");
    }

    private void registerJmxHints(RuntimeHints hints) {
        // OperatingSystemMXBean used in ResourceMonitor via reflection
        registerReflectionSafe(hints, "com.sun.management.OperatingSystemMXBean");
        registerReflectionSafe(
                hints, "com.sun.management.internal.OperatingSystemImpl");
    }

    private void registerSpringAopHints(RuntimeHints hints) {
        // Spring AOP classes used by proxies (e.g., for SpringDoc/Swagger)
        registerReflectionSafe(
                hints,
                "org.springframework.aop.framework.AdvisedSupport",
                "org.springframework.aop.framework.AdvisedSupport$MethodCacheKey",
                "org.springframework.aop.framework.CglibAopProxy",
                "org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor",
                "org.springframework.aop.framework.JdkDynamicAopProxy",
                "org.springframework.aop.framework.DefaultAopProxyFactory",
                "org.springframework.aop.framework.ProxyFactory",
                "org.springframework.aop.framework.AopProxyUtils");
    }

    private void registerSpringDocHints(RuntimeHints hints) {
        // SpringDoc/Swagger classes that use reflection
        registerReflectionSafe(
                hints,
                "org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc",
                "org.springdoc.webmvc.ui.SwaggerIndexPageTransformer",
                "org.springdoc.webmvc.ui.SwaggerConfig",
                "org.springdoc.webmvc.ui.SwaggerConfigResource",
                "org.springdoc.ui.SwaggerWelcomeCommon",
                "org.springdoc.webmvc.ui.SwaggerWebMvcConfigurer",
                "org.springdoc.core.service.AbstractRequestService",
                "org.springdoc.core.service.GenericResponseService",
                "org.springdoc.core.service.OpenAPIService",
                "org.springdoc.core.service.OperationService",
                "org.springdoc.core.service.GenericParameterService",
                "org.springdoc.core.service.RequestBodyService",
                "org.springdoc.core.service.SecurityService",
                "org.springdoc.webmvc.api.WebMvcOpenApiResource",
                "org.springdoc.core.configuration.SpringDocConfiguration");
    }

    private void registerReflection(RuntimeHints hints, String... classNames) {
        for (String className : classNames) {
            try {
                hints.reflection()
                        .registerType(
                                Class.forName(className),
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_DECLARED_METHODS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS,
                                MemberCategory.ACCESS_PUBLIC_FIELDS);
            } catch (ClassNotFoundException e) {
                // Class not on classpath, skip
            }
        }
    }

    /**
     * Same as registerReflection but silently ignores classes that may not be available (e.g.
     * optional dependencies, proprietary module classes).
     */
    private void registerReflectionSafe(RuntimeHints hints, String... classNames) {
        for (String className : classNames) {
            try {
                // Use Class.forName with initialize=false to avoid triggering native
                // static initializers (e.g. OperatingSystemImpl.initialize0()) that fail
                // during AOT processing
                Class<?> clazz =
                        Class.forName(className, false, getClass().getClassLoader());
                hints.reflection()
                        .registerType(
                                clazz,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_DECLARED_METHODS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // Optional class, skip
            } catch (UnsatisfiedLinkError e) {
                // Class has native static initializer that can't load during AOT, skip
            }
        }
    }

    /**
     * Register classes for JNI access (needed for native AWT libraries that use FindClass).
     */
    private void registerJniHints(RuntimeHints hints, String... classNames) {
        for (String className : classNames) {
            try {
                Class<?> clazz =
                        Class.forName(className, false, getClass().getClassLoader());
                hints.jni()
                        .registerType(
                                clazz,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_DECLARED_METHODS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS,
                                MemberCategory.ACCESS_PUBLIC_FIELDS);
            } catch (ClassNotFoundException | UnsatisfiedLinkError e) {
                // Class not on classpath or has native initializer, skip
            }
        }
    }

    /**
     * Same as registerJniHints but silently ignores optional/internal classes.
     */
    private void registerJniHintsSafe(RuntimeHints hints, String... classNames) {
        for (String className : classNames) {
            try {
                Class<?> clazz =
                        Class.forName(className, false, getClass().getClassLoader());
                hints.jni()
                        .registerType(
                                clazz,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_DECLARED_METHODS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS,
                                MemberCategory.ACCESS_PUBLIC_FIELDS);
            } catch (ClassNotFoundException | NoClassDefFoundError | UnsatisfiedLinkError e) {
                // Optional internal class, skip
            }
        }
    }
}
