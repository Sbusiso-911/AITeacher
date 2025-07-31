package com.playstudio.aiteacher.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {

    interface TextExtractionCallback {
        fun onTextExtracted(extractedText: String)
        fun onError(errorMessage: String)
    }

    /**
     * Saves an image bitmap to cache directory and returns its URI
     */
    fun saveImageToCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val file = File.createTempFile(
                "img_${System.currentTimeMillis()}",
                ".jpg",
                context.externalCacheDir
            )
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts text from an image using ML Kit
     */
    fun extractTextFromImage(context: Context, uri: Uri, callback: TextExtractionCallback) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    callback.onTextExtracted(visionText.text)
                }
                .addOnFailureListener { e ->
                    callback.onError("Image text extraction failed: ${e.message}")
                }
        } catch (e: Exception) {
            callback.onError("Image processing error: ${e.message}")
        }
    }

    /**
     * Extracts text from various document types (PDF, DOC, DOCX)
     */
    fun extractTextFromDocument(context: Context, uri: Uri, callback: TextExtractionCallback) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val mimeType = context.contentResolver.getType(uri)
                val text = when (mimeType) {
                    "application/pdf" -> extractTextFromPdf(context, inputStream)
                    "application/msword" -> extractTextFromDoc(inputStream)
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        extractTextFromDocx(inputStream)
                    else -> throw Exception("Unsupported document type: $mimeType")
                }
                callback.onTextExtracted(text)
            } ?: callback.onError("Could not open document")
        } catch (e: Exception) {
            callback.onError("Document processing error: ${e.message}")
        }
    }

    /**
     * Extracts text from PDF documents using PDFBox
     */
    private fun extractTextFromPdf(context: Context, inputStream: InputStream): String {
        PDFBoxResourceLoader.init(context)
        val document = PDDocument.load(inputStream)
        return try {
            PDFTextStripper().getText(document)
        } finally {
            document.close()
        }
    }

    /**
     * Extracts text from legacy Word (.doc) documents
     */
    private fun extractTextFromDoc(inputStream: InputStream): String {
        val doc = HWPFDocument(inputStream)
        return try {
            WordExtractor(doc).text
        } finally {
            doc.close()
        }
    }

    /**
     * Extracts text from modern Word (.docx) documents
     */
    private fun extractTextFromDocx(inputStream: InputStream): String {
        val doc = XWPFDocument(inputStream)
        return try {
            XWPFWordExtractor(doc).text
        } finally {
            doc.close()
        }
    }
}