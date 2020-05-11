package com.example.demo.view

import com.example.demo.controller.*
import javafx.collections.FXCollections
import javafx.geometry.Orientation
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.input.KeyCombination
import javafx.scene.layout.AnchorPane
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import tornadofx.*
import java.io.File

class MainView() : View("Certs Tool - copyright tranthinh9981@gmail.com") {

    override val root = tabpane()
    val controller: MainController by inject()
    var vendors = controller.vendors
    var restfiles = controller.restfiles
    var emptyfiles = controller.emptyfiles
    var expressions = controller.expressions

    var restFileModel = RESTFileModel()
    var vendorModel = VendorcertModel(Vendorcert("", "", "", "", "", "", "", ""))
    var lastSavedModel = vendorModel
    var expressionModel = ExpressionModel(Expression())

    var readme = controller.readme

    init {
        with(root) {
            primaryStage.isMaximized = true
            tab("Cert Files") {
                isClosable = false
                splitpane {
                    setDividerPositions(0.27)
                    borderpane {
                        top {
                            toolbar {
                                menubutton("File") {
                                    item("New", KeyCombination.keyCombination("Shortcut+N")).action { newFiles() }
                                    item("Save", KeyCombination.keyCombination("Shortcut+S")).action { saveFiles() }
                                    separator()
                                    item("Load", KeyCombination.keyCombination("Shortcut+L")).action { loadFiles() }
                                }
                                separator {}
                                label("MF-Dir")
                                textfield(configScope.metricfamiliesDirProperty)
                                separator()
                                //button("Validate All").action { validateAll() }
                                menubutton("Deliver to") {
                                    item("REST").action { deliverREST() }
                                    item("On-demand").action { deliverOndemand() }
                                }

                            }
                        }
                        center {
                            tableview(vendors) {
                                column("Names", Vendorcert::nameProperty)
                                column("File Path", Vendorcert::filepathProperty)
                                smartResize()
                                vendorModel.rebindOnChange(this) {
                                    item = selectedItem ?: Vendorcert("", "", "", "", "", "", "", "")
                                    expressions.removeAll { true }
                                    expressions.apply {
                                        addAll(item.expressionList.list)
                                    }

                                    lastSavedModel = VendorcertModel(item.copy())
                                }
                                contextmenu {
                                    item("Remove").action {
                                        selectedItem?.apply { removeVendor() }
                                    }
                                }
                            }
                        }
                    }
                    borderpane() {
                        top {
                            toolbar {
                                //button("Save to cache").action { saveDetails() }
                                //separator()
                                button("Reset").action { resetDetails() }
                                button("Save & validate").action { validateDetails() }
                                button("Make correct").action { correctDetails() }
                            }
                        }
                        center {
                            splitpane(Orientation.VERTICAL) {
                                setDividerPositions(0.55)
                                pane {
                                    form {
                                        fitToParentSize()
                                        fieldset("Vendorcert Details") {
                                            field("Properties") {
                                                textfield("Current Content").isDisable = true
                                                textfield("Correct Content").isDisable = true
                                            }
                                            field("Name") {
                                                textfield(vendorModel.name)
                                                textfield(vendorModel.nameSuggestion).isEditable = false
                                            }
                                            field("Version") {
                                                textfield(vendorModel.version)
                                                textfield(vendorModel.versionSuggestion).isEditable = false
                                            }
                                            field("Copyright") {
                                                textfield(vendorModel.copyright)
                                                textfield(vendorModel.copyrightSuggestion).isEditable = false
                                            }
                                            field("Author") {
                                                textfield(vendorModel.author)
                                                textfield(vendorModel.authorSuggestion).isEditable = false
                                            }
                                            field("File Name") {
                                                textfield(vendorModel.filename)
                                                textfield(vendorModel.filenameSuggesstion).isEditable = false
                                            }
                                            field("MIB") {
                                                textfield(vendorModel.mib)
                                                //textfield().isEditable = false
                                            }
                                            field("Documentation") {
                                                textfield(vendorModel.documentation)
                                                //textfield().isEditable = false
                                            }
                                            field("DisplayName") {
                                                textfield(vendorModel.displayname)
                                                //textfield().isEditable = false
                                            }
                                        }
                                    }
                                }
                                splitpane(Orientation.VERTICAL) {
                                    tableview(expressions) {
                                        column("*Expression", Expression::groupNameProperty).makeEditable()
                                        column<Expression, String>(
                                                "*Metricfamily",
                                                { it.value.metricfamilyProperty.select(Expression.Metricfamily::nameProperty) }
                                        ).makeEditable()
                                        column<Expression, String>(
                                                "*Using Version",
                                                { it.value.metricfamilyProperty.select(Expression.Metricfamily::versionProperty) }
                                        ).makeEditable()
                                        column<Expression, String>(
                                                "Model Version",
                                                { it.value.metricfamilyProperty.select(Expression.Metricfamily::versionModelProperty) }
                                        )
                                        column<Expression, String>(
                                                "Correct Version",
                                                { it.value.metricfamilyProperty.select(Expression.Metricfamily::versionSuggestionProperty) }
                                        )
                                        column("New Metrics", Expression::newMetricListProperty)
                                        column("Action", Expression::newMetricListProperty).cellFormat {
                                            val view = this.tableView
                                            graphic = hbox(spacing = 20) {
                                                button("Edit Path").action {
                                                    view.selectWhere { it == rowItem }
                                                    editPathMF()
                                                }
                                            }
                                        }
                                        column<Expression, String>(
                                                "Using Path",
                                                { it.value.metricfamilyProperty.select(Expression.Metricfamily::filepathProperty) }
                                        )
                                        column<Expression, String>(
                                                "DataModel Path",
                                                { it.value.metricfamilyProperty.select(Expression.Metricfamily::filepathDataModelProperty) }
                                        )

                                        smartResize()
                                        bindSelected(expressionModel)
                                        regainFocusAfterEdit()
                                        contextmenu {
                                            item("New").action {
                                                newEditorNamesValues()
                                            }
                                            item("Remove").action {
                                                selectedItem?.apply { removeExpression() }
                                            }
                                            separator()
                                            item("Open Editor").action {
                                                selectedItem?.apply { openEditorNamesValues() }
                                            }

                                        }
                                    }
                                    tableview(expressionModel.nameMaps) {
                                        column("*Names", ValuesNames::namesProperty).makeEditable().isSortable = false
                                        column("*Values", ValuesNames::valuesProperty).makeEditable().isSortable = false
                                    }
                                }

                            }
                        }
                    }


                }
            }
            tab("REST Tool") {
                isClosable = false
                borderpane {
                    top {
                        toolbar() {
                            menubutton("File") {
                                item("Load").action { loadRESTFiles() }
                                separator {}
                                item("Clear All").action { restfiles.clear() }
                            }
                            separator {}
                            label("DA Host:Port")
                            textfield(configScope.hostportProperty) {
                            }
                            label("Timeout(sec)")
                            textfield(configScope.timeoutProperty) {
                            }
                        }
                    }
                    center {
                        splitpane() {
                            vbox {
                                separator { }
                                label("- Your VC/MF File List")
                                tableview(restfiles) {
                                    column("Action", RESTFile::nameProperty).cellFormat {
                                        graphic = hbox(spacing = 5) {
                                            button("GET").action { rowItem.get() }
                                            button("POST").action { rowItem.post() }
                                            button("PUT").action { rowItem.put() }
                                            enableWhen(rowItem.isFinishedProperty)
                                        }
                                    }
                                    column("File Name", RESTFile::nameProperty)
                                    column("Type", RESTFile::filetypeProperty)
                                    column("Status", RESTFile::statusProperty)
                                    column("Full Path", RESTFile::filepathProperty)
                                    bindSelected(restFileModel)
                                    smartResize()
                                    maxHeight += 300.0
                                    contextmenu {
                                        item("Remove").action {
                                            selectedItem?.apply { restfiles.remove(selectedItem) }
                                        }
                                    }
                                }
                                separator { }
                                label("- Saved VC/MF on DA Server")
                                tableview(emptyfiles) {
                                    column("Action", RESTFile::nameProperty).cellFormat {
                                        graphic = hbox(spacing = 5) {
                                            button("GET").action { rowItem.get() }
                                            button("PUT").action { putWithEditor(rowItem) }
                                        }
                                    }
                                    column("*Name", RESTFile::nameProperty).makeEditable()
                                    column("Type", RESTFile::filetypeProperty)
                                    column("Status", RESTFile::statusProperty)
                                    smartResize()
                                    bindSelected(restFileModel)
                                    maxHeight += 200.0
                                }
                            }
                            anchorpane() {
                                label("Responses")
                                textarea(restFileModel.response) {
                                    AnchorPane.setLeftAnchor(this, 5.0)
                                    AnchorPane.setRightAnchor(this, 5.0)
                                    AnchorPane.setTopAnchor(this, 25.0)
                                    AnchorPane.setBottomAnchor(this, 5.0)
                                }

                            }
                        }
                    }
                }
            }
            tab("On-demand Tool") {
                isClosable = false
                form {
                    fieldset("Infomation") {
                        fieldset("Ticket") {
                            field("KANBAN ID") {
                                textfield(readme.kanbanIDProperty)
                            }
                            field("Additional Description") {
                                textfield(readme.additionalProperty)
                            }
                        }
                        fieldset("Readme File") {
                            field("Manufacturer") {
                                textfield(readme.manufacturerProperty)
                            }
                            field("Model") {
                                textfield(readme.modelProperty)
                            }
                            field("CAPM Supported") {
                                textfield(readme.supportedProperty)
                            }
                            field("Grouping?") {
                                combobox(readme.groupingProperty, FXCollections.observableArrayList("no", "yes"))
                            }

                            field("VC/MF List") {
                                vbox {
                                    tableview(readme.rest) {
                                        column("File Name", RESTFile::nameProperty)
                                        column("Type", RESTFile::filetypeProperty)
                                        column("Full Path", RESTFile::filepathProperty)
                                        maxHeight += 250.0
                                        contextmenu {
                                            item("Load").action {
                                                loadReadmeREST()
                                            }
                                            separator()
                                            item("Remove").action {
                                                selectedItem?.apply { removeReadmeREST(selectedItem) }
                                            }
                                            item("Clear").action {
                                                clearReadmeREST()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        buttonbar {
                            button("Generate to ..").action { generateReadme() }
                        }
                    }
                }
            }
        }
    }

    fun newFiles() {
        controller.createNewCerts()
    }

    fun loadFiles() {
        val fileChooser = configScope.fileChooser
        fileChooser.getExtensionFilters().add(FileChooser.ExtensionFilter("Vendorcerts", "*.xml"))
        val fileList = fileChooser.showOpenMultipleDialog(currentWindow)
        if (fileList != null) {
            fileList.forEach {
                controller.loadFile(it.path)
            }
        }
    }

    fun loadRESTFiles() {
        val fileChooser = configScope.fileChooser
        fileChooser.getExtensionFilters().add(FileChooser.ExtensionFilter("Vendorcerts,Metricfamily", "*.xml"))
        val fileList = fileChooser.showOpenMultipleDialog(currentWindow)
        if (fileList != null) {
            fileList.forEach {
                controller.loadRESTFile(it.path)
            }
        }
    }

    fun saveFiles() {
        val dirChooser = configScope.dirChooser
        val file = dirChooser.showDialog(currentWindow)
        if (file != null) {
            val dirPath = file.path
            controller.saveFiles(dirPath)
        }
    }

    fun mapNamesValues() {

    }

    fun saveDetails() {

    }

    fun resetDetails() {
        vendorModel.item = lastSavedModel.item.copy()
        expressions.removeAll { true }
        expressions.addAll(vendorModel.item.expressionList.list)
        vendorModel.commit()
    }

    fun validateDetails() {
        vendorModel.commit()
        expressionModel.commit()
        vendorModel.item.validate()
        expressions.forEach {
            it.validate()
        }
    }

    fun validateAll() {
        vendors.forEach {
            it.validate()
        }
    }

    fun correctDetails() {
        vendorModel.item.makeCorrect()
    }

    fun openEditorNamesValues() {
        controller.editExpression(expressionModel)
    }

    fun newEditorNamesValues() {
        controller.newExpression()
        vendorModel.item.expressionList.list = arrayListOf<Expression>().apply { addAll(expressions) }.observable()
    }

    fun removeExpression() {
        if (Alert(Alert.AlertType.WARNING, "Remove ${expressionModel.groupName}?", ButtonType.YES, ButtonType.NO)
                        .showAndWait().get() == ButtonType.YES) {
            expressions.remove(expressionModel.item)
            vendorModel.item.expressionList.list = expressions
            vendorModel.commit(vendorModel.expressionList)
        }
    }

    fun removeVendor() {
        val vendor = vendorModel.item
        if (Alert(Alert.AlertType.WARNING, "Delete ${vendor!!.name}?", ButtonType.YES, ButtonType.NO)
                        .showAndWait().get() == ButtonType.YES) {
            controller.removeCerts(vendor)
        }
    }

    fun editPathMF() {
        expressionModel.commit()
        val fileChooser = configScope.fileChooser
        fileChooser.getExtensionFilters().add(FileChooser.ExtensionFilter("Metricfamily", "*.xml"))
        val file = fileChooser.showOpenDialog(currentWindow)
        if (file != null) controller.editPathMF(expressionModel, file)
    }

    fun putWithEditor(rest: RESTFile) {
        controller.putWithEditor(rest)
    }

    fun deliverREST() {
        vendors.forEach {
            restfiles.add(RESTFile.load(it.filepath))
        }
        Alert(Alert.AlertType.INFORMATION, "Have delivered!").showAndWait()
    }

    fun deliverOndemand() {
        vendors.forEach {
            readme.rest.add(RESTFile.load(it.filepath))
        }
        Alert(Alert.AlertType.INFORMATION, "Have delivered!").showAndWait()
    }

    fun loadReadmeREST() {
        val fileChooser = configScope.fileChooser
        fileChooser.getExtensionFilters().add(FileChooser.ExtensionFilter("Vendorcerts,Metricfamily", "*.xml"))
        val fileList = fileChooser.showOpenMultipleDialog(currentWindow)
        if (fileList != null) {
            fileList.forEach {
                controller.loadReadmeREST(it.path)
            }
        }
    }

    fun removeReadmeREST(rest: RESTFile?) {
        if (Alert(Alert.AlertType.WARNING, "Delete ${rest!!.name} (${rest!!.filepath})?", ButtonType.YES, ButtonType.NO)
                        .showAndWait().get() == ButtonType.YES) {
            readme.rest.remove(rest)
        }
    }

    fun clearReadmeREST() {
        if (Alert(Alert.AlertType.WARNING, "Clear all?", ButtonType.YES, ButtonType.NO)
                        .showAndWait().get() == ButtonType.YES) {
            readme.rest.removeAll { true }
        }
    }

    fun generateReadme() {
        val dirChooser = configScope.dirChooser
        val file = dirChooser.showDialog(currentWindow)
        if (file != null) {
            val dirPath = file.path
            controller.generateReadme(readme, dirPath)
        }

    }
}


