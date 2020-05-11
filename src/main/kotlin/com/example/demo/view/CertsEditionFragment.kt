package com.example.demo.view

import com.example.demo.controller.*
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import tornadofx.*

class CertsEditorFragment : Fragment() {
    val vendorModel: VendorcertModel by param()
    val xmlCerts: XMLCertsController by inject()
    val mainController: MainController by inject()
    override val root = form()

    init {
        with(root) {
            minWidth = 350.0
            fieldset("Cert File") {
                fieldset("Vendorcerts") {
                    field("Vendorcert Name") {
                        textfield(vendorModel.name).required()
                    }
                }
                buttonbar {
                    button("Submit").enableWhen { vendorModel.valid }.action {
                        vendorModel.commit()
                        mainController.addCertsToCertsList(vendorModel.item)
                        close()
                    }
                }
            }
        }
    }
}

class ExpressionEditorFragment : Fragment() {
    val isNew: Boolean by param(false)
    val mainController: MainController by inject()
    val xmlCertsController: XMLCertsController by inject()
    val expressionModel: ExpressionModel by param(ExpressionModel(xmlCertsController.newExpression("", "")))

    var validated = SimpleBooleanProperty(true)
    var editedValues = SimpleStringProperty("")
    var editedNames = SimpleStringProperty("")

    override val root = pane()

    init {
        editedNames.value = expressionModel.item.getNames().joinToString("\n")
        editedValues.value = expressionModel.item.getValues().joinToString("\n")
        with(root) {
            minWidth = 350.0
            fitToParentSize()
            form {
                fieldset() {
                    fieldset("Expression Details") {
                        field("Group Name") { textfield(expressionModel.groupName) }
                        field("Metricfamily") { textfield(expressionModel.metricfamily.select(Expression.Metricfamily::nameProperty)) }
                        if (!isNew) field("Version") { textfield(expressionModel.metricfamily.select(Expression.Metricfamily::versionProperty)) }

                        fieldset(labelPosition = Orientation.VERTICAL) {
                            hbox {
                                field("Expressions Names") { textarea(editedNames) { fitToParentHeight() } }
                                field("Expressions Values") { textarea(editedValues) { fitToParentHeight() } }
                            }
                        }
                    }
                }
                fieldset() {
                    buttonbar {
                        button("Submit").enableWhen { validated }.action {
                            isGeneratedExpression()
                            createNew()
                            expressionModel.item.change()
                            if (expressionModel.item.validate()) {
                                if (isNew) mainController.addExpression(expressionModel.item)
                                expressionModel.commit()
                                close()
                            }
                        }
                    }
                }
            }
        }
    }

    fun createNew() {
        expressionModel.item.groupName = expressionModel.groupName.value
        expressionModel.item.metricfamily = Expression.Metricfamily(expressionModel.metricfamily.select(Expression.Metricfamily::nameProperty).value)
    }

    fun isGeneratedExpression() {
        if (editedNames.value!!.isEmpty() && editedValues.value.isEmpty()) {
            expressionModel.nameMaps.value = arrayListOf<ValuesNames>().observable()
        } else {
            val nameLines = editedNames.value.lines()
            val valueLines = editedValues.value.lines()
            val iNum = if (nameLines.size > valueLines.size) nameLines.size else valueLines.size
            val newMaps = arrayOfNulls<ValuesNames>(iNum)
            newMaps.forEachIndexed { index, valuesNames ->
                val name = nameLines.getOrElse(index) { "" }
                val value = valueLines.getOrElse(index) { "" }
                newMaps.set(index, ValuesNames(name, value))
            }
            expressionModel.nameMaps.value = newMaps.toMutableList().observable()
            expressionModel.commit(expressionModel.nameMaps)
        }
    }
}

class RESTEditorFragment : Fragment() {
    val rest: RESTFile by param()
    override val root = pane()

    init {
        with(root) {
            form {
                fieldset("Editor for ${rest.name}", labelPosition = Orientation.VERTICAL) {
                    field("XML Content", Orientation.VERTICAL) {
                        textarea(rest.requestProperty)
                    }
                    buttonbar {
                        button("PUT").action {
                            rest.put(rest.request)
                            close()
                        }
                    }
                }
            }
        }
    }
}