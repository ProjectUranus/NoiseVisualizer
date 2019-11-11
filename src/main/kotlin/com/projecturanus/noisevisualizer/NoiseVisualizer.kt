package com.projecturanus.noisevisualizer

import javafx.animation.TranslateTransition
import javafx.application.Application
import javafx.beans.property.BooleanProperty
import javafx.scene.*
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Box
import javafx.scene.transform.Rotate
import javafx.stage.Stage
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

var enableColoring: BooleanProperty = false.toProperty()
var enableFiltering: BooleanProperty = false.toProperty()

class NoiseVisualizer : Application() {

    override fun start(primaryStage: Stage) {
        println(blockMap)
        val root = Group()
        for (x in 0 until 16) {
            for (y in 0 until 128) {
                for (z in 0 until 16) {
                    // Creating an object of the class Box
                    val box = Box()
                    box.setOnMouseMoved { }
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

        val scene = Scene(root, 800.0, 600.0, true, SceneAntialiasing.BALANCED)

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
        scene.setOnKeyPressed {
            val animation = TranslateTransition(Duration.millis(100.0))
            animation.node = camera
            when (it.code) {
                KeyCode.W -> animation.byZ = 300.0
                KeyCode.A -> animation.byX = -300.0
                KeyCode.S -> animation.byZ = -300.0
                KeyCode.D -> animation.byX = 300.0
            }
            animation.play()
        }
        scene.fill = Color.WHITE
        scene.camera = camera

        primaryStage.scene = scene
        primaryStage.show()
        showConfigStage()
    }

    fun showConfigStage() {
        val stage = Stage()
        val pane = ConfigView().root
        val scene = Scene(pane, 600.0, 400.0)
        stage.scene = scene
        stage.show()
    }
}

fun syncColor() {
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
                    }
                }
                if (enableColoring.value) {
                    if (noise in lowValueColoring..highValueColoring) {
                        (box.material as PhongMaterial).diffuseColor = Color.rgb(abs(noise * 255).toInt() + 1, 0, 0)
                    }
                }
            }
        }
    }
}

class ConfigView : View() {
    override val root = gridpane {
        paddingAll = 10.0
        row {
            label { text = "Frequency" }
            val slider = slider(0, 1)
            slider.valueProperty().addListener { observable, oldValue, newValue -> noise.m_frequency = newValue.toFloat() }
            label(slider.valueProperty())
        }
        row {
            label { text = "Noise type" }
            combobox(null, FastNoise.NoiseType.values().toList()) {
                value = FastNoise.NoiseType.Perlin
                selectionModel.selectedItemProperty().onChange { noise.m_noiseType = it ?: FastNoise.NoiseType.Perlin }
            }
        }
        row {
            label { text = "Interpolation (Only in Fractal noise)" }
            combobox(null, FastNoise.Interp.values().toList()) {
                value = FastNoise.Interp.Quintic
                selectionModel.selectedItemProperty().onChange { noise.m_interp = it ?: FastNoise.Interp.Quintic }
            }
        }
        row {
            checkbox {
                text = "Enable coloring"
                selectedProperty().value = false
                enableColoring.bind(selectedProperty())
            }
        }
        row {
            label { text = "Light up threshold" }
            lateinit var range: Label
            RangeSlider(-1.0, 1.0, -0.4, 0.6).attachTo(this) {
                disableProperty().value = true
                enableColoring.onChange { disableProperty().set(!it) }
                lowValueProperty().onChange { lowValueColoring = it; range.text = "$lowValue~$highValue" }
                highValueProperty().onChange { highValueColoring = it; range.text = "$lowValue~$highValue" }
            }
            range = label()
        }
        row {
            checkbox {
                text = "Enable filtering"
                selectedProperty().value = false
                enableFiltering.bind(selectedProperty())
            }
        }
        row {
            label { text = "Filtering threshold" }
            lateinit var range: Label
            RangeSlider(-1.0, 1.0, -0.4, 0.6).attachTo(this) {
                disableProperty().value = true
                enableFiltering.onChange { disableProperty().set(!it) }
                lowValueProperty().onChange { lowValueFiltering = it; range.text = "$lowValue~$highValue" }
                highValueProperty().onChange { highValueFiltering = it; range.text = "$lowValue~$highValue" }
            }
            range = label()
        }
        row {
            button("Rebuild").setOnMouseClicked { syncColor() }
        }
    }
}

fun main(args: Array<String>) {
    Application.launch(NoiseVisualizer::class.java, *args)
}
