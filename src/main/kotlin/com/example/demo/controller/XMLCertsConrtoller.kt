package com.example.demo.controller

import com.example.demo.controller.StringUtillities.Companion.addLines
import com.example.demo.controller.StringUtillities.Companion.firstRegex
import com.example.demo.view.CertsEditorFragment
import javafx.beans.property.*
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import khttp.responses.Response
import tornadofx.*
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*


class configScope : Scope() {
    companion object {
        val metricfamiliesDirProperty = SimpleStringProperty("D:\\Cert\\CAPM Tool\\datamodel\\metricfamilies")
        var hostportProperty = SimpleStringProperty("10.74.100.100:8581")
        var timeoutProperty = SimpleDoubleProperty(3.0)
        val fileChooser = FileChooser()
        val dirChooser = DirectoryChooser()
    }
}

interface validate {
    fun validate(): Boolean
    fun getValidatedFieldString(): String
    fun makeCorrect()
}

class XMLCertsController() : Controller() {

    fun load(vcfilepath: String): Vendorcert {
        val vendorcert = extractVendorInfo(vcfilepath)
        val expressionList = extractExpressionList(vcfilepath)
        val attributeList = extractAttributeList(vcfilepath)
        vendorcert.attributes = attributeList.observable()
        vendorcert.expressionList = expressionList
        vendorcert.validate()
        return vendorcert
    }

    fun extractVendorInfo(xmlPath: String): Vendorcert {
        val xmlText = File(xmlPath).readText()
        val copyright = firstRegex("""Copyright \(c\) (.*?) CA""", xmlText)
        val author = firstRegex("""Author>(.*?)<\/Author>""", xmlText)
        val version = firstRegex("""<Version>(.*?)<\/Version>""", xmlText)
        val name = firstRegex("""<FacetType.*?name="(.*?)"""", xmlText)
        val documentation = firstRegex("""<Documentation>(.*?)<\/Documentation>""", xmlText)
        val displayname = firstRegex("""<DisplayName>(.*?)<\/DisplayName>""", xmlText)
        val mib = firstRegex("""<MIB>(.*?)<\/MIB>""", xmlText)
        return Vendorcert(name, copyright, author, version, documentation, displayname, mib, xmlPath)
    }

    fun extractExpressionList(xmlPath: String): ExpressionList {
        val xmlText = File(xmlPath).readText()
        val rootExpressionText = Regex("""(?s)<Expressions>(.*?)<\/Expressions>""", RegexOption.MULTILINE).find(xmlText.toString())!!.groupValues.get(1)
        val expressionList = ExpressionList()
        val groupMatches = Regex("""(?s)(<ExpressionGroup.*?name="(.*?)".*?>.*?<\/ExpressionGroup>)""").findAll(rootExpressionText)
        groupMatches.forEach { group ->
            val (groupText, groupName) = group.destructured
            val expression = Expression(groupName, groupText)
            val (mfname) = Regex("""destCert="\{http://im.ca.com/normalizer}(.*?)"""").find(groupText)!!.destructured
            val metricfamily = Expression.Metricfamily(mfname)
            expression.metricfamily = metricfamily
            val rowMatches = Regex("""<Expression.*?destAttr=['|"](.*?)['|"]>(.*?)<\/""").findAll(groupText.replace("\\n".toRegex(), ""))
            rowMatches.forEach { row ->
                val (name, value) = row.destructured
                expression.nameMaps.add(ValuesNames(name, value))
            }
            expression.validate()
            expressionList.list.add(expression)
        }
        return expressionList
    }

    fun extractAttributeList(xmlPath: String): MutableList<ValuesNames> {
        val text = File(xmlPath).readText()
        val attributes = arrayListOf<ValuesNames>()
        val re = Regex("""(?s)<Attribute (.*?)</Attribute>""").findAll(text.replace(Regex("(?s)<!--(.*?)-->"), ""))
        re.forEach {
            val re2 = Regex("""(?s)name="(.*?)".*<Source>(.*?)</Source>""").find(it.groupValues.get(1))
            val (name, oid) = re2!!.destructured
            attributes.add(ValuesNames(name, oid))
        }
        return attributes
    }

    companion object {
        fun findMFFilePath(MFName: String): String {
            val MFDir = configScope.metricfamiliesDirProperty.value
            val filter = File(MFDir).walk().filter { it.isFile && it.name.equals("im.ca.com-metricfamilies-${MFName}.xml") }
            if (!filter.none()) {
                return filter.first().path
            } else {
                return ""
            }
        }
    }

    fun writeToFile(dirPath: String, vendorcert: Vendorcert) {
        var text = File(vendorcert.filepath).readText()
        val copyright = vendorcert.copyright
        text = Regex("""(Copyright \(c\) )(.*?)( CA)""").replace(text, "$1${copyright}$3")

        val author = vendorcert.author
        text = Regex("""(Author>)(.*?)(<\/Author>)""").replace(text, "$1${author}$3")

        val version = vendorcert.version
        text = Regex("""(<Version>)(.*?)(<\/Version>)""").replace(text, "$1${version}$3")

        val vcname = vendorcert.name
        text = Regex("""(<FacetType.*?name=")(.*?)(")""").replace(text, "$1${vcname}$3")

        val documentation = vendorcert.documentation
        text = Regex("""(<Documentation>)(.*?)(<\/Documentation>)""").replace(text, "$1${documentation}$3")

        val displayname = vendorcert.displayname
        text = Regex("""(<DisplayName>)(.*?)(<\/DisplayName>)""").replace(text, "$1${displayname}$3")

        val mib = vendorcert.mib
        text = Regex("""(<MIB>)(.*?)(<\/MIB>)""").replace(text, "$1${mib}$3")

        val expressionrawtext = vendorcert.expressionList!!.toRawText()
        text = Regex("""(?s)<Expressions>(.*?)</Expressions>""").replace(text, "<Expressions>\r\n${expressionrawtext}\r\n</Expressions>")
        text = format(text)

        val filename = vendorcert.filename
        val path = "${dirPath}\\${filename}"
        var file = File(path)
        if (file.exists()) {
            if (Alert(Alert.AlertType.WARNING, "Overwrite ${file.path}?", ButtonType.YES, ButtonType.NO)
                            .showAndWait().get() == ButtonType.YES) {
                file.writeText(text)
                File(file.parent).mkdirs()
                file.createNewFile()
                vendorcert.filepath = path
            } else {
                Alert(Alert.AlertType.WARNING, "${file.name} has not been writen.")
            }
        }
        vendorcert.expressionList.list.forEach {
            if (it.newMetricList.isNotEmpty()) {
                val filename = File(it.metricfamily.filepathDataModel).name
                val path = "${dirPath}\\${filename}"
                val file = File(path)
                if (Alert(Alert.AlertType.WARNING, "Overwrite ${file.path}?", ButtonType.YES, ButtonType.NO)
                                .showAndWait().get() == ButtonType.YES) {
                    val version = it.metricfamily.version
                    var text = it.metricfamily.getRawTextUsingPath()
                    text = Regex("""(<Version>)(.*?)(<\/Version>)""").replace(text, "$1${version}$3")
                    File(file.parent).mkdirs()
                    file.createNewFile()
                    file.writeText(text)
                } else {
                    Alert(Alert.AlertType.WARNING, "${file.name} has not been writen.")
                }

            }
        }
    }

    fun newExpression(mfname: String = "", groupName: String = ""): Expression {
        val rawText = "<ExpressionGroup name=\"${groupName}\" destCert=\"{http://im.ca.com/normalizer}${mfname}\">\n" +
                "<Expression destAttr=\"Indexes\">Index</Expression>\n" +
                "<Expression destAttr=\"Names\">Name</Expression>\n" +
                "<Expression destAttr=\"Descriptions\">Description</Expression>\n" +
                "</ExpressionGroup>"

        val expression = Expression(groupName, rawText)
        val metricfamily = Expression.Metricfamily(mfname)
        expression.metricfamily = metricfamily
        val rowMatches = Regex("""<Expression.*?destAttr=['|"](.*?)['|"]>(.*?)<\/""").findAll(rawText.replace("\\n".toRegex(), ""))
        rowMatches.forEach { row ->
            val (name, value) = row.destructured
            expression.nameMaps.add(ValuesNames(name, value))
        }
        return expression
    }

    fun format(xmlText: String): String {
        return xmlText
    }
}

class StringUtillities() {
    companion object {
        fun addLines(source: String, text: String): String {
            var result = source
            if (result.isEmpty()) result += "${text}" else result += "\n${text}"
            return result
        }

        fun firstRegex(pattern: String, text: String): String {
            return Regex(pattern).find(text)?.groupValues?.get(1) ?: ""
        }
    }
}

class ValuesNames(names: String, values: String) {
    val namesProperty = SimpleStringProperty(this, "Names", names)
    var names by namesProperty
    val valuesProperty = SimpleStringProperty(this, "Values", values)
    var values by valuesProperty
    override fun toString(): String {
        return "${names}\t${values}"
    }
}

class ExpressionList(list: MutableList<Expression> = arrayListOf()) : validate {
    val listProperty = SimpleListProperty<Expression>(this, "Name", list.observable())
    var list by listProperty

    fun toRawText(): String {
        var result = ""
        list.forEach {
            result = addLines(result, it.rawText)
        }
        return result
    }

    override fun validate(): Boolean {
        return true
    }


    override fun getValidatedFieldString(): String {
        val sb = StringBuilder()
        list.forEach {
            sb.append(it.groupName + " - " + it.metricfamily!!.name + "\n")
            it.validate()
            sb.append(it.getValidatedFieldString() + "\n\n")
        }
        return sb.toString()

    }

    override fun makeCorrect() {
        list.forEach {
            it.makeCorrect()
        }
    }

    fun toGroupNames(): ArrayList<String> {
        var results = arrayListOf<String>()
        list.forEach {
            results.add(it.groupName)
        }
        return results
    }

    fun toMFNames(): ArrayList<String> {
        var results = arrayListOf<String>()
        list.forEach {
            if (it.metricfamily != null) {
                results.add(it.metricfamily!!.name)
            }
        }
        return results
    }

    fun change(groupName: String) {
        val expression = list.find { it.groupName.equals(groupName) }
        if (expression != null) {
            expression!!.change()
        }
    }

    fun remove(groupName: String) {
        list.removeIf { it.groupName.equals(groupName) }
    }

    fun removeAll() {
        list.removeAll { true }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        list.forEach {
            sb.append(it.toString() + "\n")
        }
        return sb.toString()
    }

}

class Expression(groupName: String = "", rawText: String = "", nameMaps: MutableList<ValuesNames> = arrayListOf()) : validate {
    var isValid = true
    val groupNameProperty = SimpleStringProperty(this, "Group Name", groupName)
    var groupName by groupNameProperty
    val rawTextProperty = SimpleStringProperty(this, "Raw Text", rawText)
    var rawText by rawTextProperty
    val nameMapsProperty = SimpleListProperty<ValuesNames>(this, "Name/Value", nameMaps.observable())
    var nameMaps by nameMapsProperty
    val metricfamilyProperty = SimpleObjectProperty(this, "MF Object", Metricfamily(""))
    var metricfamily by metricfamilyProperty
    val filenameSuggestionProperty = SimpleStringProperty(this, "File Name Suggestion", "")
    var filenameSuggestion by filenameSuggestionProperty
    val newMetricListProperty = SimpleListProperty<String>(this, "New Metric List", arrayListOf<String>().observable())
    var newMetricList by newMetricListProperty

    init {
    }

    override fun validate(): Boolean {
        metricfamily.versionSuggestion = ""
        filenameSuggestion = ""
        newMetricList = arrayListOf<String>().observable()
        isValid = true

        val filepath = metricfamily.filepath
        if (!File(filepath).exists()) {
            Alert(Alert.AlertType.ERROR, "${metricfamily.name} is not found").showAndWait()
            isValid = false
        } else {
            metricfamily.filename = File(filepath).name
            metricfamily.filenameProperty.value = metricfamily!!.filename

            if (!metricfamily.filename!!.equals("im.ca.com-metricfamilies-${metricfamily.name}.xml")) {
                filenameSuggestion = "im.ca.com-metricfamilies-${metricfamily.name}.xml"
                filenameSuggestionProperty.value = filenameSuggestion
            }

            newMetricList = metricfamily!!.findNewMetrics(getNames()).observable()
            newMetricListProperty.value = newMetricList.observable()

            val modelVersion = firstRegex("""<Version>(.*?)<\/Version>""", metricfamily.getRawTextModelPath()).toDouble()
            val usingVersion = firstRegex("""<Version>(.*?)<\/Version>""", metricfamily.getRawTextUsingPath()).toDouble()
            if (newMetricList.isNotEmpty()) {
                if (usingVersion != (modelVersion + 0.1)) {
                    metricfamily.versionSuggestion = BigDecimal(modelVersion + 0.1).setScale(1, RoundingMode.HALF_EVEN).toString()
                    metricfamily.versionSuggestionProperty.value = metricfamily.versionSuggestion
                }
            } else {
                if (metricfamily.version.toDouble() != modelVersion) {
                    metricfamily.versionSuggestion = modelVersion.toString()
                    metricfamily.versionSuggestionProperty.value = metricfamily.versionSuggestion
                }
            }
        }
        return isValid
    }

    override fun getValidatedFieldString(): String {
        return "Validated? ${isValid}\n" +
                "version: ${metricfamily!!.version} -> ${metricfamily.versionSuggestion}\n" +
                "filename: ${metricfamily!!.filename} -> ${filenameSuggestion}\n" +
                "New metrics list: ${newMetricList}"
    }

    override fun makeCorrect() {
        if (metricfamily.versionSuggestion.isNotEmpty()) {
            metricfamily.version = metricfamily.versionSuggestion
            metricfamily.versionProperty.value = metricfamily.version
        }
    }

    fun getNames(): ArrayList<String> {
        var results = arrayListOf<String>()
        nameMaps.forEach {
            results.add(it.names)
        }
        return results
    }

    fun getValues(): ArrayList<String> {
        var results = arrayListOf<String>()
        nameMaps.forEach {
            results.add(it.values)
        }
        return results
    }

    fun change() {
        updateName()
        val currentExpressionStringList = getElement()
        val currentSize = currentExpressionStringList.size - 1
        val newMaps = nameMaps
        var newSize = newMaps.size - 1

        newMaps.forEachIndexed { lineIndex, newElement ->
            if (lineIndex <= currentSize) {
                val oldString = currentExpressionStringList.get(lineIndex)
                update(oldString, newElement)
            } else if (lineIndex > currentSize) {
                append(newElement)
            }
        }

        if (newSize < currentSize) {
            if (newSize == -1) newSize = 0
            for (i in currentSize downTo newSize + 1) {
                remove(currentExpressionStringList.get(i))
            }
        }
        clearEmptyLines()
    }

    fun updateName() {
        rawText = rawText.replace(Regex("""(<ExpressionGroup name=").*?(" destCert="\{http://im.ca.com/normalizer\}).*?(">)"""), "$1${groupName}$2${metricfamily.name}$3")
    }

    fun update(oldString: String, newValuesNames: ValuesNames) {
        val newString = "<Expression destAttr=\"${newValuesNames.names}\">${newValuesNames.values}</Expression>"
        rawText = rawText.replace(oldString, newString)
    }

    fun append(newValuesNames: ValuesNames) {
        val newElementText = "<Expression destAttr=\"${newValuesNames.names}\">${newValuesNames.values}</Expression>"
        rawText = rawText.replace("</ExpressionGroup>", "${newElementText}\r\n</ExpressionGroup>")
    }

    fun remove(oldString: String) {
        rawText = rawText.replace(oldString, "")
    }

    fun clearEmptyLines() {
        rawText = rawText.replace(Regex("""^[ \t]*\r?\n""", RegexOption.MULTILINE), "")
    }

    fun getElement(): MutableList<String> {
        val results = arrayListOf<String>()
        Regex("""(?s)(<Expression destAttr="(.*?)">(.*?)</Expression>)""").findAll(rawText)!!.forEach {
            results.add(it.groupValues[0])
        }
        return results
    }


    override fun toString(): String {
        val mfString = metricfamily.toString()
        return "${mfString}\ngroupname:${groupName}\nmaps:\n${nameMaps}\nrawtext:\n${rawText}"
    }

    class Metricfamily(name: String = "") {
        val filenameProperty = SimpleStringProperty(this, "File Name", "")
        var filename by filenameProperty
        val versionProperty = SimpleStringProperty(this, "Version", "")
        var version by versionProperty
        val versionModelProperty = SimpleStringProperty(this, "Version Model", "")
        var versionModel by versionModelProperty
        val versionSuggestionProperty = SimpleStringProperty(this, "Version Suggestion", "")
        var versionSuggestion by versionSuggestionProperty
        val filepathProperty = SimpleStringProperty(this, "File Path", "")
        var filepath by filepathProperty
        val nameProperty = SimpleStringProperty(this, "Name", name)
        var name by nameProperty
        val filepathDataModelProperty = SimpleStringProperty(this, "File Path Data Model", "")
        var filepathDataModel by filepathDataModelProperty

        init {
            filepath = XMLCertsController.findMFFilePath(nameProperty.value)
            filepathProperty.value = filepath
            if (filepath.isNotEmpty()) {
                filepathDataModelProperty.value = filepath
                filepathDataModel = filepath
                version = firstRegex("""<Version>(.*?)<\/Version>""", getRawTextModelPath())
                versionProperty.value = version
                versionModel = version
                versionModelProperty.value = versionModel
                filename = File(filepath).name
                filenameProperty.value = filename
            }
        }

        fun getParentDir(): String {
            return File(filepath).parent
        }

        fun findNewMetrics(candidateMetrics: ArrayList<String>): ArrayList<String> {
            val existsMetrics = getMetricNames()
            val results = arrayListOf<String>()
            candidateMetrics.forEach { metricName ->
                if (existsMetrics.filter { metricName.equals(it) }.isEmpty()) {
                    results.add(metricName)
                }
            }
            return results
        }

        fun getRawTextModelPath(): String {
            val file = File(filepathDataModel)
            if (!file.exists()) return ""
            return file.readText()
        }

        fun getRawTextUsingPath(): String {
            val file = File(filepath)
            if (!file.exists()) return ""
            return file.readText()
        }

        fun getMetricNames(): ArrayList<String> {
            val results = arrayListOf<String>()
            val text = getRawTextModelPath()
            val matches = Regex("""<Attribute.*?name="(.*?)".*?>""").findAll(text)
            matches.forEach { match ->
                val (name) = match.destructured
                results.add(name)
            }
            return results
        }

        fun getValidatedFieldString(): String {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun toString(): String {
            return "name:${filename}\nversion:${version}\nfilepath:${filepath}"
        }

        fun copy(): Metricfamily {
            val copiedMetricfamily = Metricfamily("")
            copiedMetricfamily.filename = filename
            copiedMetricfamily.version = version
            copiedMetricfamily.versionModel = versionModel
            copiedMetricfamily.versionSuggestion = versionSuggestion
            copiedMetricfamily.filepath = filepath
            copiedMetricfamily.name = name
            copiedMetricfamily.filepathDataModel = filepathDataModel
            return copiedMetricfamily
        }
    }


}

class Vendorcert(name: String, copyright: String, author: String, version: String, documentation: String, displayname: String, mib: String, filepath: String) : validate {

    val nameProperty = SimpleStringProperty(this, "Name", name)
    var name by nameProperty
    val copyrightProperty = SimpleStringProperty(this, "Copyright", copyright)
    var copyright by copyrightProperty
    val authorProperty = SimpleStringProperty(this, "Author", author)
    var author by authorProperty
    val versionProperty = SimpleStringProperty(this, "Version", version)
    var version by versionProperty
    val documentationProperty = SimpleStringProperty(this, "Documentation", documentation)
    var documentation by documentationProperty
    val displaynameProperty = SimpleStringProperty(this, "Displayname", displayname)
    var displayname by displaynameProperty
    val mibProperty = SimpleStringProperty(this, "MIB", mib)
    var mib by mibProperty
    val filepathProperty = SimpleStringProperty(this, "File Path", filepath)
    var filepath by filepathProperty
    val filenameProperty = SimpleStringProperty(this, "File Name", "")
    var filename by filenameProperty
    val filenameSuggestionProperty = SimpleStringProperty(this, "File Name Suggestion", "")
    var filenameSuggestion by filenameSuggestionProperty
    val nameSuggestionProperty = SimpleStringProperty(this, "Name Suggestion", "")
    var nameSuggestion by nameSuggestionProperty
    val copyrightSuggestionProperty = SimpleStringProperty(this, "Copyright Suggestion", "")
    var copyrightSuggestion by copyrightSuggestionProperty
    val authorSuggestionProperty = SimpleStringProperty(this, "Author Suggestion", "")
    var authorSuggestion by authorSuggestionProperty
    val versionSuggestionProperty = SimpleStringProperty(this, "Version Suggestion", "")
    var versionSuggestion by versionSuggestionProperty
    val isValidProperty = SimpleBooleanProperty(this, "File Name", true)
    var isValid by isValidProperty
    val expressionListProperty = SimpleObjectProperty<ExpressionList>(this, "File Name", ExpressionList())
    var expressionList by expressionListProperty
    val attributesProperty = SimpleListProperty<ValuesNames>(arrayListOf<ValuesNames>().observable())
    var attributes by attributesProperty

    init {
        if (filepath.isNotEmpty()) filename = File(filepath).name
    }

    fun getParentDirPath(): String {
        return File(filepath).parent
    }

    fun getReadmeText(): String {
        val sb1 = java.lang.StringBuilder()
        expressionList.list.forEach {
            sb1.appendln("VC:${name}\t\t\t\t\t\t\t| MF:${it.metricfamily.name}")
            it.nameMaps.forEach {
                val expName = it.names
                if (Regex("""Indexes|Names|Descriptions""").matches(expName)) return@forEach
                val expValue = it.values
                val sb2 = java.lang.StringBuilder("")
                attributes.forEach {
                    val attName = it.names
                    val attOID = it.values
                    val isFound = Regex("""\b(${attName})\b""").containsMatchIn(expValue)
                    if (isFound) {
                        if (sb2.isEmpty()) {
                            sb2.appendln("OID:${attOID}\t\t\t\t\t| Metric:${expName}")
                        } else {
                            sb2.appendln("OID:${attOID}")
                        }
                    }
                }
                sb1.append(sb2.toString())
            }
        }
        return sb1.toString()
    }

    override fun validate(): Boolean {
        copyrightSuggestion = ""
        authorSuggestion = ""
        versionSuggestion = ""
        filenameSuggestion = ""
        nameSuggestion = ""
        isValid = true
        val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
        if (!copyright.equals(currentYear)) {
            copyrightSuggestion = currentYear
            isValid = false
        }


        val NameWithoutMib = name.replace("Mib", "")
        if (!name.equals("${NameWithoutMib}Mib")) {
            nameSuggestion = "${NameWithoutMib}Mib"
            isValid = false
        }
        if (!filename!!.equals("im.ca.com-certifications-snmp-${NameWithoutMib}Mib.xml")) {
            filenameSuggestion = "im.ca.com-certifications-snmp-${NameWithoutMib}Mib.xml"
            isValid = false
        }

        if (!author.equals("CA")) {
            authorSuggestion = "CA"
            isValid = false
        }



        if (version.toDouble() < 1.0) {
            versionSuggestion = "1.0"
            isValid = false
        } else if (version.length < 3) {
            versionSuggestion = "${version.toDouble()}"
        }

        return isValid
    }

    override fun getValidatedFieldString(): String {
        return "Validated? ${isValid}\n" +
                "copyright: ${copyright} -> ${copyrightSuggestion}\n" +
                "author: ${author} -> ${authorSuggestion}\n" +
                "version: ${version} -> ${versionSuggestion}\n" +
                "filename: ${filename} -> ${filenameSuggestion}\n"
    }

    override fun makeCorrect() {
        if (nameSuggestion.isNotEmpty()) {
            name = nameSuggestion
            nameProperty.value = name
        }
        if (filenameSuggestion.isNotEmpty()) {
            filename = filenameSuggestion
            filenameProperty.value = filename
        }
        if (authorSuggestion.isNotEmpty()) {
            author = authorSuggestion
            authorProperty.value = author
        }
        if (copyrightSuggestion.isNotEmpty()) {
            copyright = copyrightSuggestion
            copyrightProperty.value = copyright
        }
        if (versionSuggestion.isNotEmpty()) {
            version = versionSuggestion
            versionProperty.value = version
        }

        expressionList.makeCorrect()
    }

    override fun toString(): String {

        return "name:${name}\n" +
                "copyright:${copyright}\n" +
                "author:${author}\n" +
                "version:${version}\n" +
                "documentation:${documentation}\n" +
                "displayname:${displayname}\n" +
                "mib:${mib}\n" +
                "filepath:${filepath}\n" +
                expressionList.toString()
    }

    fun copy(): Vendorcert {
        val name = this.name
        val copyright = this.copyright
        val author = this.author
        val version = this.version
        val documentation = this.documentation
        val displayname = this.displayname
        val mib = this.mib
        val filepath = this.filepath
        val copiedVendorcert = Vendorcert(name, copyright, author, version, documentation, displayname, mib, filepath)
        copiedVendorcert.filename = this.filename
        copiedVendorcert.filenameSuggestion = this.filenameSuggestion
        copiedVendorcert.nameSuggestion = this.nameSuggestion
        copiedVendorcert.copyrightSuggestion = this.copyrightSuggestion
        copiedVendorcert.authorSuggestion = this.authorSuggestion
        copiedVendorcert.versionSuggestion = this.versionSuggestion
        copiedVendorcert.isValid = this.isValid
        val copiedExpressionList = arrayListOf<Expression>()
        this.expressionList.list.forEach {
            val eGroupname = it.groupName
            val eRawtext = it.rawText
            val eNameMaps = arrayListOf<ValuesNames>()
            it.nameMaps.forEach {
                eNameMaps.add(ValuesNames(it.names, it.values))
            }
            val copiedExpression = Expression(eGroupname, eRawtext, eNameMaps)
            copiedExpression.isValid = it.isValid
            copiedExpression.filenameSuggestion = it.filenameSuggestion
            copiedExpression.metricfamily = it.metricfamily.copy()
            copiedExpression.newMetricList = it.newMetricList.toList().observable()
            copiedExpressionList.add(copiedExpression)
        }
        copiedVendorcert.expressionList.list = SimpleListProperty<Expression>(copiedExpressionList.observable())
        return copiedVendorcert
    }
}

class ExpressionModel(expression: Expression) : ItemViewModel<Expression>(expression) {
    val groupName = bind(Expression::groupNameProperty)
    val rawText = bind(Expression::rawTextProperty)
    val nameMaps = bind(Expression::nameMapsProperty)
    val metricfamily = bind(Expression::metricfamilyProperty)
    val filenameSuggestion = bind(Expression::filenameSuggestionProperty)
    val newMetricList = bind(Expression::newMetricListProperty)


}

class VendorcertModel(vendorcert: Vendorcert) : ItemViewModel<Vendorcert>(vendorcert) {
    val name = bind(Vendorcert::nameProperty)
    val copyright = bind(Vendorcert::copyrightProperty)
    val author = bind(Vendorcert::authorProperty)
    val version = bind(Vendorcert::versionProperty)
    val documentation = bind(Vendorcert::documentationProperty)
    val displayname = bind(Vendorcert::displaynameProperty)
    val mib = bind(Vendorcert::mibProperty)
    val filepath = bind(Vendorcert::filepathProperty)

    val nameSuggestion = bind(Vendorcert::nameSuggestionProperty)
    var copyrightSuggestion = bind(Vendorcert::copyrightSuggestionProperty)
    var authorSuggestion = bind(Vendorcert::authorSuggestionProperty)
    var versionSuggestion = bind(Vendorcert::versionSuggestionProperty)
    var filename = bind(Vendorcert::filenameProperty)
    var filenameSuggesstion = bind(Vendorcert::filenameSuggestionProperty)
    var expressionList = bind(Vendorcert::expressionListProperty)

}

enum class RESTType {
    Vendorcert, Metricfamily, Priority
}

class RESTFileModel() : ItemViewModel<RESTFile>() {
    val response = bind(RESTFile::responseProperty)
    var name = bind(RESTFile::nameProperty)
    var id = bind(RESTFile::idProperty)
    var request = bind(RESTFile::requestProperty)
}

class RESTFile(name: String? = "", filepath: String? = "", filetype: RESTType? = RESTType.Vendorcert) {
    val nameProperty = SimpleStringProperty(name)
    var name by nameProperty
    val filepathProperty = SimpleStringProperty(filepath)
    var filepath by filepathProperty
    val filetypeProperty = SimpleStringProperty(filetype.toString())
    var filetype by filetypeProperty
    val responseProperty = SimpleStringProperty("")
    var response by responseProperty
    val requestProperty = SimpleStringProperty("")
    var request by requestProperty
    val statusProperty = SimpleStringProperty("")
    var status by statusProperty
    val idProperty = SimpleStringProperty("")
    var id by idProperty
    val isFinishedProperty = SimpleBooleanProperty(true)
    var isFinished by isFinishedProperty

    companion object {
        fun load(xmlPath: String): RESTFile {
            var restfile = RESTFile()
            val xmlText = File(xmlPath).readText()
            val name = firstRegex("""<FacetType.*?name="(.*?)"""", xmlText)
            val type = firstRegex("""<FacetType.*?descriptorClass="com\.ca\.im\.core\.datamodel\.certs\.(.*?)"""", xmlText)
            if (type.equals("NormalizedFacetDescriptorImpl"))
                restfile = RESTFile(name, xmlPath, RESTType.Metricfamily)
            else if (type.equals("CertificationFacetDescriptorImpl")) {
                restfile = RESTFile(name, xmlPath, RESTType.Vendorcert)
            }
            return restfile
        }

        val VCURL = "/typecatalog/certifications/snmp"
        val MFURL = "/typecatalog/metricfamilies"
        val VPRIURL = "/rest/vendorpriorities"

        fun GET(rest: RESTFile): Response {
            var Domain = configScope.hostportProperty.value
            if (rest.filetype.equals(RESTType.Priority.toString())) {
                val filterURL = "${URL(rest)}/filtered"
                val fillterData = """<FilterSelect xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="filter.xsd">
                                <Filter>
                                <MetricFamilyVendorPriority.MetricFamilyID type="EQUAL">{http://im.ca.com/normalizer}${rest.name}</MetricFamilyVendorPriority.MetricFamilyID>
                                </Filter>
                                <Select use="exclude" isa="exclude">
                                <MetricFamilyVendorPriority use="exclude"/>
                                </Select>
                                </FilterSelect>"""
                var response = POST(filterURL, fillterData)
                val (id) = Regex("<ID>(.*?)</ID>").find(response.text)!!.destructured
                response = khttp.get("${URL(rest)}/$id", timeout = configScope.timeoutProperty.value)
                if (response.statusCode == 200) rest.id = id
                return response
            } else {
                return khttp.get("${URL(rest)}/${rest.name}", timeout = configScope.timeoutProperty.value)
            }
        }

        fun POST(url: String, data: String): Response {
            var Domain = configScope.hostportProperty.value

            val response = khttp.post(
                    url = url,
                    headers = mapOf("Content-Type" to "application/xml"),
                    data = data,
                    timeout = configScope.timeoutProperty.value)
            return response
        }

        fun POST(rest: RESTFile): Response {
            var Domain = configScope.hostportProperty.value
            val response = khttp.post(
                    url = "${URL(rest)}",
                    headers = mapOf("Content-Type" to "application/xml"),
                    data = File(rest.filepath).readText(),
                    timeout = configScope.timeoutProperty.value)
            return response
        }

        fun PUT(url: String, data: String): Response {
            var Domain = configScope.hostportProperty.value
            val response = khttp.put(
                    url = url,
                    headers = mapOf("Content-Type" to "application/xml"),
                    data = data,
                    timeout = configScope.timeoutProperty.value)
            return response
        }

        fun PUT(rest: RESTFile): Response {
            var Domain = configScope.hostportProperty.value

            val response = khttp.put(
                    url = "${URL(rest)}/${rest.name}",
                    headers = mapOf("Content-Type" to "application/xml"),
                    data = File(rest.filepath).readText(),
                    timeout = configScope.timeoutProperty.value)
            return response
        }

        fun URL(rest: RESTFile): String {
            var Domain = configScope.hostportProperty.value

            when (rest.filetype) {
                RESTType.Vendorcert.toString() -> {
                    return "http://$Domain$VCURL"
                }
                RESTType.Metricfamily.toString() -> {
                    return "http://$Domain$MFURL"
                }
                RESTType.Priority.toString() -> {
                    return "http://$Domain$VPRIURL"
                }
            }
            return "http://$Domain"
        }

    }

    fun get() {
        isFinished = false
        status = "waiting.."
        val thread = Thread {
            try {
                val res = GET(this)
                response = res.text
                status = res.statusCode.toString()
            } catch (e: Exception) {
                status = "Time out"
            }
            isFinished = true
        }
        thread.start()
    }

    fun post() {
        isFinished = false
        status = "waiting.."
        val thread = Thread {
            try {
                val res = POST(this)
                response = res.text
                status = res.statusCode.toString()
            } catch (e: Exception) {
                status = "Time out"
            }
            isFinished = true
        }
        thread.start()
    }

    fun put() {
        isFinished = false
        status = "waiting.."
        val thread = Thread {
            try {
                val res = PUT(this)
                response = res.text
                status = res.statusCode.toString()
            } catch (e: Exception) {
                status = "Time out"
            }
            isFinished = true
        }
        thread.start()
    }

    fun put(xmlContent: String) {
        isFinished = false
        status = "waiting.."
        val thread = Thread {
            try {
                val page = if (filetype.equals(RESTType.Priority.toString())) id else name
                val res = PUT("${URL(this)}/${page}", xmlContent)
                response = res.text
                status = res.statusCode.toString()
            } catch (e: Exception) {
                status = "Time out"
            }
            isFinished = true
        }
        thread.start()
    }


}

class ReadmeFile(kanbanID: String, additional: String, manufacturer: String, model: String, supported: String, grouping: String, rest: ArrayList<RESTFile>) {
    val kanbanIDProperty = SimpleStringProperty(kanbanID)
    var kanbanID by kanbanIDProperty
    val additionalProperty = SimpleStringProperty(additional)
    var additional by additionalProperty
    val manufacturerProperty = SimpleStringProperty(manufacturer)
    var manufacturer by manufacturerProperty
    val modelProperty = SimpleStringProperty(model)
    var model by modelProperty
    val supportedProperty = SimpleStringProperty(supported)
    var supported by supportedProperty
    val groupingProperty = SimpleStringProperty(grouping)
    var grouping by groupingProperty
    val restProperty = SimpleListProperty<RESTFile>(rest.observable())
    var rest by restProperty

    companion object {
        val xmlCertsController = XMLCertsController()
    }

    fun writeTo(dir: File) {
        val readmeText = CertsEditorFragment::class.java.getClassLoader().getResource("readme.txt").readText()
        val mfDir = "${dir.path}\\On-demand_${kanbanID}\\${kanbanID}_${additional}\\MetricFamilies"
        val vcDir = "${dir.path}\\On-demand_${kanbanID}\\${kanbanID}_${additional}\\VendorCerts"

        val sb = StringBuilder()
        rest.forEach {
            val f = File(it.filepath)
            if (it.filetype.equals(RESTType.Vendorcert.toString())) {
                sb.append(xmlCertsController.load(it.filepath).getReadmeText())
                f.copyTo(File("$vcDir\\${f.name}"), true)
            } else {
                f.copyTo(File("$mfDir\\${f.name}"), true)
            }
        }
        val oidText = sb.toString()

        val newText = Regex("""(?s)<property 1>(.*)<property 2>(.*)<property 3>(.*)<property 4>(.*)<property 5>(.*)""")
                .replace(readmeText, "${manufacturer}$1${model}$2${supported}$3${oidText}$4${grouping}$5")

        File("${dir.path}\\On-demand_${kanbanID}\\${kanbanID}_${additional}\\readme.txt").writeText(newText)
        if (grouping.equals("yes")) {
            val priorityText = CertsEditorFragment::class.java.getClassLoader().getResource("Priority.txt").readText()
            File("${dir.path}\\On-demand_${kanbanID}\\${kanbanID}_${additional}\\Priority.txt").writeText(priorityText)
        }
    }
}