package com.projecturanus.noisevisualizer

import javafx.animation.TranslateTransition
import javafx.application.Application
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.scene.*
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Box
import javafx.scene.transform.Rotate
import javafx.util.Duration
import org.controlsfx.control.RangeSlider
import tornadofx.*
import java.util.*
import kotlin.math.abs

private var blockMap = (0 until 16).map { (0 until 256).map { arrayListOf<Box>() }.toCollection(arrayListOf()) }.toCollection(arrayListOf())

private var mousePosX: Double = 0.0
private var mousePosY: Double = 0.0
private var mouseOldX: Double = 0.0
private var mouseOldY: Double = 0.0

val noise = FastNoise(Random().nextInt())

// Coloring threshold
var lowValueColoring = 0.0
var highValueColoring = 0.0

// Filtering threshold
var lowValueFiltering = 0.0
var highValueFiltering = 0.0

var instantMode: BooleanProperty = false.toProperty()
var enableColoring: BooleanProperty = false.toProperty()
var enableFiltering: BooleanProperty = false.toProperty()
var coloredNum: IntegerProperty = 0.toProperty()
var filteredNum: IntegerProperty = 0.toProperty()

var mouseOnPos = Triple(0, 0, 0).toProperty()
var selectedPos  = Triple(0, 0, 0).toProperty()

class NoiseVisualizer : App(MainView::class)

fun syncColor() {
    coloredNum.value = 0
    filteredNum.value = 0
    for (x in 0 until 16) {
        for (y in 0 until 128) {
            for (z in 0 until 16) {
                val box = blockMap[x][y][z]
                val noise = noise.GetNoise(x.toFloat(), y.toFloat(), z.toFloat()).toDouble()
                box.visibleProperty().value = true
                (box.material as PhongMaterial).diffuseColor = Color.grayRgb(abs(noise * 255).toInt())
                if (enableFiltering.value) {
                    if (noise !in lowValueFiltering..highValueFiltering) {
                        box.visibleProperty().value = false
                        filteredNum.value++
                        if (enableColoring.value) {
                            if (noise in lowValueColoring..highValueColoring) {
                                coloredNum.value--
                            }
                        }
                    }
                }
                if (enableColoring.value) {
                    if (noise in lowValueColoring..highValueColoring) {
                        (box.material as PhongMaterial).diffuseColor = Color.rgb(abs(noise * 255).toInt() + 1, 0, 0)
                        coloredNum.value++
                    }
                }
            }
        }
    }
}

fun initSubScene(): SubScene {
    println(blockMap)
    var root = Group()
    for (x in 0 until 16) {
        for (y in 0 until 128) {
            for (z in 0 until 16) {
                // Creating an object of the class Box
                val box = Box()
                box.setOnMouseMoved { mouseOnPos.value = Triple(x, y, z) }
                box.setOnMouseClicked { selectedPos.value = Triple(x, y, z) }
                box.width = 50.0
                box.height = 50.0
                box.depth = 50.0
                box.userData = Triple(x, y, z)
                box.material = PhongMaterial()
                box.translateX = x * 70.0
                box.translateY = y * 70.0
                box.translateZ = z * 70.0
                blockMap[x][y].add(z, box)
                root.children.add(box)
            }
        }
    }
    syncColor()

    val scene = SubScene(root, 600.0, 600.0, true, SceneAntialiasing.BALANCED)

    val rotateX = Rotate(30.0, 500.0, 500.0, 500.0, Rotate.X_AXIS)
    val rotateY = Rotate(20.0, 500.0, 500.0, 500.0, Rotate.Y_AXIS)
    root.transforms.addAll(rotateX, rotateY)
    root.children.add(AmbientLight(Color.WHITE))

    scene.setOnMousePressed { me ->
        mouseOldX = me.sceneX
        mouseOldY = me.sceneY
    }
    scene.setOnMouseDragged { me ->
        mousePosX = me.sceneX
        mousePosY = me.sceneY
        rotateX.angle = rotateX.angle - (mousePosY - mouseOldY)
        rotateY.angle = rotateY.angle + (mousePosX - mouseOldX)
        mouseOldX = mousePosX
        mouseOldY = mousePosY
    }
    val camera = PerspectiveCamera(true)
    camera.nearClip = 0.1
    camera.farClip = 10000.0
    camera.translateX = 0.0
    camera.translateY = 0.0
    camera.translateZ = -100.0
    camera.fieldOfView = 80.0
    scene.fill = Color.WHITE
    scene.camera = camera
    return scene
}

class MainView : View() {
    override val root = splitpane {
        title = "Noise Visualizer"
        primaryStage.width = 900.0
        primaryStage.isResizable = false
        val subScene = initSubScene()
        opcr(this, subScene) { }
        configPane
        setOnKeyPressed {
            val animation = TranslateTransition(Duration.millis(100.0))
            animation.node = subScene.camera
            when (it.code) {
                KeyCode.W -> animation.byZ = 300.0
                KeyCode.A -> animation.byX = -300.0
                KeyCode.S -> animation.byZ = -300.0
                KeyCode.D -> animation.byX = 300.0
            }
            animation.play()
        }
    }

    val configPane = gridpane {
        paddingAll = 10.0
        prefWidth = 200.0
        row {
            label {
                textProperty().bind(mouseOnPos.stringBinding { "Current pos: ${mouseOnPos.value}" })
            }
        }
        row {
            label {
                textProperty().bind(mouseOnPos.stringBinding { "Noise value: ${noise.GetNoise(mouseOnPos.value.first.toFloat(), mouseOnPos.value.second.toFloat(), mouseOnPos.value.third.toFloat())}" })
            }
        }
        row {
            checkbox {
                text = "Instant mode"
                selectedProperty().value = false
                instantMode.bind(selectedProperty())
            }
        }
        row {
            label("Frequency")
        }
        row {
            val slider = slider(0, 1)
            slider.valueProperty().addListener { observable, oldValue, newValue -> noise.m_frequency = newValue.toFloat(); if (instantMode.value) { syncColor() } }
            label(slider.valueProperty())
        }
        row {
            label { text = "Noise type" }
            combobox(null, FastNoise.NoiseType.values().toList()) {
                value = FastNoise.NoiseType.Perlin
                selectionModel.selectedItemProperty().onChange {
                    noise.m_noiseType = it ?: FastNoise.NoiseType.Perlin
                    if (instantMode.value) { syncColor() }
                }
            }
        }
        row {
            label { text = "Interpolation (Only in Fractal noise)" }
            combobox(null, FastNoise.Interp.values().toList()) {
                value = FastNoise.Interp.Quintic
                selectionModel.selectedItemProperty().onChange { noise.m_interp = it ?: FastNoise.Interp.Quintic; if (instantMode.value) { syncColor() } }
            }
        }
        row {
            checkbox {
                text = "Enable coloring"
                selectedProperty().value = false
                enableColoring.bind(selectedProperty())
            }
        }
        lateinit var lightLabel: Label
        row {
            label { text = "Light up threshold" }
            RangeSlider(-1.0, 1.0, -0.4, 0.6).attachTo(this) {
                disableProperty().value = true
                enableColoring.onChange { disableProperty().set(!it) }
                lowValueProperty().onChange { lowValueColoring = it; lightLabel.text = String.format("%.5f~%.5f", lowValue, highValue); if (instantMode.value) { syncColor() } }
                highValueProperty().onChange { highValueColoring = it; lightLabel.text = String.format("%.5f~%.5f", lowValue, highValue); if (instantMode.value) { syncColor() } }
            }
        }
        row {
            lightLabel = label()
        }
        row { label { textProperty().bind(coloredNum.stringBinding { "Colorized amount: \n $it / ${65536 - filteredNum.intValue()} (${String.format("%.2f", ((it?.toDouble() ?: 0.0) / (65536 - filteredNum.intValue())) * 100)}%)" }) } }
        row {
            checkbox {
                text = "Enable filtering"
                selectedProperty().value = false
                enableFiltering.bind(selectedProperty())
            }
        }
        lateinit var filterLabel: Label
        row {
            label { text = "Filtering threshold" }
            RangeSlider(-1.0, 1.0, -0.4, 0.6).attachTo(this) {
                disableProperty().value = true
                enableFiltering.onChange { disableProperty().set(!it) }
                lowValueProperty().onChange { lowValueFiltering = it; filterLabel.text = String.format("%.5f~%.5f", lowValue, highValue); if (instantMode.value) { syncColor() } }
                highValueProperty().onChange { highValueFiltering = it; filterLabel.text = String.format("%.5f~%.5f", lowValue, highValue); if (instantMode.value) { syncColor() } }
            }
        }
        row {
            filterLabel = label()
        }
        row { label { textProperty().bind(filteredNum.stringBinding { "Filtered amount: \n $it / 65536 (${String.format("%.2f", ((it?.toDouble() ?: 0.0) / 65536.0) * 100)}%)" }) } }
        row { label { textProperty().bind(filteredNum.stringBinding { "Shown amount: \n ${65536 - (it?.toInt() ?: 0)} / 65536 (${String.format("%.2f", ((65536 - (it?.toDouble() ?: 0.0)) / 65536.0) * 100)}%)" }) } }
        row {
            button("Rebuild").setOnMouseClicked { syncColor() }
        }
    }
}

fun main(args: Array<String>) {
    Application.launch(NoiseVisualizer::class.java, *args)
}
