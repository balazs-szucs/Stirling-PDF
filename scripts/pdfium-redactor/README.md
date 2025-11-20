# Embedded PDFium Redactor Runtime

This directory holds the unbundled sources for the Node.js helper that drives
EmbedPDF/PDFium-based redaction.

The production runtime that ships with the backend is stored inside
`app/core/src/main/resources/static/pdfium/pdfium-redactor.zip`. That archive is
unpacked on-demand when the backend needs to execute the helper.

If you modify `pdfium-redactor.mjs`, rebuild the archive so that the packaged
runtime stays in sync:

1. Install the required EmbedPDF packages locally (one-time):

   ```bash
   npm install @embedpdf/engines@1.4.1 @embedpdf/models@1.4.1 @embedpdf/pdfium@1.4.1
   ```

2. Create a staging folder (outside the repo or under `/tmp`). Copy the three
   packages above into `node_modules/@embedpdf/` inside that folder.
3. Copy `pdfium-redactor.mjs` and `package.json` into the staging folder root.
4. From the staging parent directory, create the archive using the JDK `jar`
   tool:

   ```bash
   jar cf pdfium-redactor.zip pdfium-redactor
   ```

5. Replace `app/core/src/main/resources/static/pdfium/pdfium-redactor.zip` with
   the new archive.

The backend automatically extracts the archive at runtime, so no additional build
steps are needed once the zip is updated.
