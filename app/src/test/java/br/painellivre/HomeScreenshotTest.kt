package br.painellivre

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import br.painellivre.ui.cockpit.Palette
import br.painellivre.ui.home.HomeFormat
import br.painellivre.ui.home.HomeVehicleBinder
import br.painellivre.ui.home.VehicleUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Desenha a tela inicial fora do carro. Confere que a noite fica escura e o dia fica claro,
 * e grava os PNGs para o olho humano. Não liga MapLibre (o Application do app fica de fora).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w1280dp-h720dp-land-hdpi")
class HomeScreenshotTest {

    @Test fun nightSampleIsDarkAndDaySampleIsLight() {
        val night = draw(Palette.NIGHT, VehicleUiState.sample())
        val day = draw(Palette.DAY, VehicleUiState.sample())
        val empty = draw(Palette.NIGHT, VehicleUiState())
        save("home-noite", night)
        save("home-dia", day)
        save("home-sem-dados", empty)

        assertEquals(1920, night.width)
        assertEquals(1080, night.height)
        // O rodapé é cartão opaco: noite escura, dia clara. Não amostra o número grande.
        assertTrue("noite ${regionLum(night, 80, 1000, 200, 40)}", regionLum(night, 80, 1000, 200, 40) < 50)
        assertTrue("dia ${regionLum(day, 80, 1000, 200, 40)}", regionLum(day, 80, 1000, 200, 40) > 180)
        assertTrue(night.sameAs(day).not())
        assertTrue(night.sameAs(empty).not())
        assertTrue(File("/opt/cursor/artifacts/screenshots/home-noite.png").length() > 20_000)
    }

    private fun draw(palette: Palette, state: VehicleUiState): Bitmap {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
        val metrics = activity.resources.displayMetrics
        metrics.density = 1.5f
        metrics.scaledDensity = 1.5f
        metrics.densityDpi = 240
        val root = activity.layoutInflater.inflate(R.layout.activity_launcher, FrameLayout(activity), false)
        root.findViewById<View>(R.id.backdrop).background = null
        val backdrop = br.painellivre.ui.cockpit.BackdropDrawable(palette.dark)
        root.findViewById<android.widget.ImageView>(R.id.backdrop).setImageDrawable(backdrop)
        palette.apply(root, metrics.density)
        HomeVehicleBinder.bind(root, HomeFormat.present(state), palette)
        root.findViewById<android.widget.ImageView>(R.id.carArt).setImageDrawable(
            br.painellivre.ui.cockpit.CarArtDrawable(palette.dark, HomeFormat.carVisual(state)),
        )
        root.findViewById<TextView>(R.id.greet).text = "Boa noite"
        root.findViewById<TextView>(R.id.greetSub).text = "Mapa no painel"
        root.findViewById<TextView>(R.id.clock).text = "18:02"
        root.findViewById<TextView>(R.id.date).text = "SEX, 23 DE OUT"
        root.findViewById<View>(R.id.weatherCard).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.weatherTemp).text = "22°"
        root.findViewById<TextView>(R.id.weatherDesc).text = "Nublado"
        root.findViewById<android.widget.ImageView>(R.id.weatherIcon).setImageDrawable(
            br.painellivre.ui.cockpit.WeatherIconDrawable(
                br.painellivre.info.WeatherRepository.Kind.CLOUDY,
                day = false,
                palette = palette,
            ),
        )
        palette.apply(root, metrics.density)
        HomeVehicleBinder.bind(root, HomeFormat.present(state), palette)

        val width = 1920
        val height = 1080
        activity.setContentView(root)
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        return bitmap
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("/opt/cursor/artifacts/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun regionLum(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int): Double {
        var sum = 0.0
        var n = 0
        for (py in y until y + h step 4) {
            for (px in x until x + w step 4) {
                val c = bitmap.getPixel(px, py)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                n++
            }
        }
        return sum / n
    }
}
