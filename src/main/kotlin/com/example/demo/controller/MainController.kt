package com.example.demo.controller

import com.example.demo.view.CertsEditorFragment
import com.example.demo.view.ExpressionEditorFragment
import com.example.demo.view.RESTEditorFragment
import javafx.stage.StageStyle
import tornadofx.*
import java.io.File
import java.io.FileOutputStream

class MainController() : Controller() {
    var vendors = arrayListOf<Vendorcert>().observable()
    var restfiles = arrayListOf<RESTFile>().observable()
    var emptyfiles = arrayListOf<RESTFile>(
            RESTFile("", "", RESTType.Vendorcert),
            RESTFile("", "", RESTType.Metricfamily),
            RESTFile("", "", RESTType.Priority)
    ).observable()
    var expressions = arrayListOf<Expression>().observable()
    var readme = ReadmeFile("DE1234567", "JUNIPER-SRX4600", "JUNIPER", "JUNIPER SRX4600", "3.6 & 3.7", "no", arrayListOf<RESTFile>())

    val xmlCertsController: XMLCertsController by inject()
    fun createNewCerts() {
        val xmlText = CertsEditorFragment::class.java.getClassLoader().getResource("vcsample.xml").readText()
        val xmlFile = File.createTempFile("vcsample",".xml")
        xmlFile.writeText(xmlText)
        val vendorModel = VendorcertModel(xmlCertsController.load(xmlFile.absolutePath))
        find<CertsEditorFragment>(mapOf(CertsEditorFragment::vendorModel to vendorModel))
                .openWindow(stageStyle = StageStyle.UTILITY, block = true)
    }

    fun addCertsToCertsList(vendorcert: Vendorcert) {
        vendors.add(vendorcert)
    }

    fun addExpression(expression: Expression) {

        expressions.add(expression)
    }

    fun removeCerts(vendorcert: Vendorcert) {
        vendors.remove(vendorcert)
    }

    fun loadFile(xmlPath: String) {
        val vendorcert = xmlCertsController.load(xmlPath)
        addCertsToCertsList(vendorcert)
    }

    fun loadRESTFile(xmlPath: String) {
        restfiles.add(RESTFile.load(xmlPath))
    }

    //
    fun saveFiles(dirPath: String) {
        vendors.forEach {
            xmlCertsController.writeToFile(dirPath, it)
        }

    }

    fun newExpression() {
        find<ExpressionEditorFragment>(mapOf(ExpressionEditorFragment::isNew to true))
                .openWindow(stageStyle = StageStyle.DECORATED, block = true)
    }

    fun editExpression(expressionModel: ExpressionModel) {
        find<ExpressionEditorFragment>(mapOf(ExpressionEditorFragment::expressionModel to expressionModel))
                .openWindow(stageStyle = StageStyle.DECORATED, block = true)
    }

    fun editPathMF(expressionModel: ExpressionModel, file: File) {
        expressionModel.item.metricfamily.filepath = file.path
        expressionModel.item.validate()
        expressionModel.commit()
    }

    fun putWithEditor(rest: RESTFile){
        find<RESTEditorFragment>(mapOf(RESTEditorFragment::rest to rest))
                .openWindow(stageStyle = StageStyle.DECORATED, block = true)
    }

    fun loadReadmeREST(xmlPath: String) {
        readme.rest.add(RESTFile.load(xmlPath))
    }

    fun generateReadme(readmeFile: ReadmeFile, dirPath: String){
        readmeFile.writeTo(File(dirPath))
    }
}