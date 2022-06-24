package org.pdflayoutsunpacker

//import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdfwriter.ContentStreamWriter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.common.PDStream
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.File
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.roundToInt


class PdfUnpacker(path: String) {

    private val file = File(path)

    companion object {
        @JvmStatic fun main(args : Array<String>) {
            val frame = JFrame("PDF Layers Unpacker")
            val button = JButton("Unpack PDF's Layout")
            val text = JLabel("Progress 0.00%")

            frame.layout = BorderLayout()
            frame.setSize(400, 200)
            frame.isResizable = false
            text.isVisible = false

            button.addMouseListener(object: MouseListener {
                override fun mouseClicked(e: MouseEvent?) {
                    val chooser = JFileChooser()
                    chooser.fileFilter = FileNameExtensionFilter("PDF file", "pdf")
                    val ret = chooser.showOpenDialog(frame)
                    if(ret == JFileChooser.APPROVE_OPTION) {
                        text.isVisible = true
                        button.isVisible = false

                        Thread {
                            PdfUnpacker(chooser.selectedFile.path).divideLayers {
                                text.text = "Progress " + ((it * 10000).roundToInt().toFloat() / 100f) + "%"
                            }
                            text.isVisible = false
                            text.text = "Progress 0.00%"
                            button.isVisible = true
                        }.start()
                    }
                }

                override fun mousePressed(e: MouseEvent?) {
                }

                override fun mouseReleased(e: MouseEvent?) {
                }

                override fun mouseEntered(e: MouseEvent?) {
                }

                override fun mouseExited(e: MouseEvent?) {
                }

            })

            frame.add(button, BorderLayout.CENTER)
            frame.add(text, BorderLayout.NORTH)
            frame.isVisible = true
            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        }
    }

    fun divideLayers(progress: (perc: Float) -> Unit) {
        val DPI = 200f
        //val doc = Loader.loadPDF(file)
        val doc = PDDocument.load(file)
        val page = doc.getPage(0)
        var width = page.mediaBox.width
        var height = page.mediaBox.height

        if(width < height) {
            val box = width
            width = height
            height = box
        }

        val catalog = doc.documentCatalog
        val oc = catalog.ocProperties
        val names = oc.groupNames

        println("width $width; height $height")

        val newDoc = PDDocument()
        var cnt = 1
        for(i in names) {
            println(i)
            if(i != "0") {
                //val tmp = Loader.loadPDF(file)
                val tmp = PDDocument.load(file)
                OCGDelete(tmp, 0, i)

                val tmpCat = tmp.documentCatalog
                val tmpOc = tmpCat.ocProperties
                val group = tmpOc.getGroup(i)
                val newOc = PDOptionalContentProperties()
                newOc.addGroup(group)
                tmpCat.ocProperties = newOc

                val image = PDFRenderer(tmp).renderImageWithDPI(0, DPI)
                tmp.close()

                val newPage = PDPage(PDRectangle(width, height))
                val pdImage = LosslessFactory.createFromImage(newDoc, image)

                val contentStream = PDPageContentStream(doc, newPage, PDPageContentStream.AppendMode.APPEND, false)
                contentStream.drawImage(pdImage, 0f, 0f, width, height)
                contentStream.close()

                newDoc.addPage(newPage)
            }
            progress((cnt++).toFloat() / names.size.toFloat())
        }
        val newPath = (if(file.parent != null) file.parent + "\\" else "") + file.nameWithoutExtension + "_new.pdf"
        if(File(newPath).exists()) File(newPath).delete()
        newDoc.save(newPath)
        newDoc.close()
        doc.close()
    }

    fun OCGDelete (doc: PDDocument, pageNum: Int, saveLayer: String) {
        val page = doc.getPage(pageNum)
        val resources = page.resources
        val parser = PDFStreamParser(page)
        parser.parse()

        val tokens = parser.tokens
        val newTokens = mutableListOf<Any>()
        val deletionIndexList = mutableListOf<List<Int>>()

        var index = 0
        while(index < tokens.size) {
            var obj = tokens[index]
            if(obj is COSName && obj == COSName.OC) {
                val startIndex = index
                index++
                if(index < tokens.size) {
                    obj = tokens[index]
                    if(obj is COSName) {
                        val prop = resources.getProperties(obj)
                        if(prop != null && prop is PDOptionalContentGroup && prop.name != saveLayer) {
                            index++
                            obj = tokens[index]
                            if(obj is Operator && obj.name == "BDC") {
                                var endIndex = -1
                                index++
                                while(index < tokens.size) {
                                    obj = tokens[index]
                                    if (obj is Operator && obj.name == "EMC")
                                    {
                                        endIndex = index
                                        break
                                    }
                                    index++
                                }
                                if(endIndex >= 0) deletionIndexList.add(listOf(startIndex, endIndex))
                            }
                        }
                    }
                }
            }

            index++
        }

        var tokenListIndex = 0
        index = 0
        while(index < deletionIndexList.size) {
            val indexes = deletionIndexList[index]
            while(tokenListIndex < indexes[0]) {
                newTokens.add(tokens[tokenListIndex])
                tokenListIndex++
            }
            tokenListIndex = indexes[1] + 1
            index++
        }
        while(tokenListIndex < tokens.size) {
            newTokens.add(tokens[tokenListIndex])
            tokenListIndex++
        }
        val stream = PDStream(doc)
        val output = stream.createOutputStream(COSName.FLATE_DECODE)
        val writer = ContentStreamWriter(output)
        writer.writeTokens(newTokens)
        output.close()
        page.setContents(stream)
    }

}