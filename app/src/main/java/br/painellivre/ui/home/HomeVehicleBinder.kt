package br.painellivre.ui.home

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import br.painellivre.R
import br.painellivre.ui.cockpit.Palette

/**
 * Aplica um [HomePresentation] nas Views da tela inicial. Não lê o carro.
 * Chamar depois do `Palette.apply`, senão o tema repinta por cima do estado.
 */
object HomeVehicleBinder {
    fun bind(root: View, presentation: HomePresentation, palette: Palette) {
        val soc = root.findViewById<TextView>(R.id.batteryValue)
        soc.text = presentation.socText
        soc.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (presentation.showPercentSign) 36f else 28f)
        soc.setTextColor(color(palette, if (presentation.showPercentSign) Tone.NORMAL else Tone.MUTED))

        root.findViewById<View>(R.id.batteryPct).visibility =
            if (presentation.showPercentSign) View.VISIBLE else View.GONE

        val knownBar = presentation.socPercent != null
        root.findViewById<View>(R.id.batteryBar).visibility = if (knownBar) View.VISIBLE else View.INVISIBLE
        root.findViewById<View>(R.id.batterySkeleton).visibility = if (knownBar) View.GONE else View.VISIBLE
        root.findViewById<ProgressBar>(R.id.batteryBar).apply {
            progress = presentation.socPercent ?: 0
            val tone = presentation.socTone
            progressTintList = ColorStateList.valueOf(color(palette, if (tone == Tone.MUTED) Tone.OK else tone))
            progressBackgroundTintList = ColorStateList.valueOf(palette.text3)
        }
        root.findViewById<SocRingView>(R.id.socRing).apply {
            percent = presentation.socPercent
            charging = presentation.charging
            trackColor = palette.text3
            fillColor = color(palette, if (presentation.socTone == Tone.MUTED) Tone.OK else presentation.socTone)
            pulseColor = palette.accent
        }

        val range = root.findViewById<TextView>(R.id.rangeValue)
        range.text = presentation.rangeText
        range.setTextColor(color(palette, if (presentation.rangeKnown) Tone.NORMAL else Tone.MUTED))
        root.findViewById<TextView>(R.id.rangeSub).apply {
            text = presentation.rangeDetail
            visibility = if (presentation.rangeDetail.isBlank()) View.GONE else View.VISIBLE
        }

        val charge = presentation.chargeText
        root.findViewById<View>(R.id.chargeRow).visibility = if (charge.isBlank()) View.GONE else View.VISIBLE
        root.findViewById<TextView>(R.id.chargeText).apply {
            text = charge
            setTextColor(if (presentation.charging) palette.ok else palette.text1)
        }
        root.findViewById<TextView>(R.id.sampleChip).apply {
            visibility = if (presentation.sample) View.VISIBLE else View.GONE
            setTextColor(palette.warn)
        }

        root.findViewById<TextView>(R.id.climateTemp).apply {
            text = presentation.cabinTemp
            setTextColor(color(palette, if (presentation.cabinKnown) Tone.NORMAL else Tone.MUTED))
        }
        tintText(root, R.id.climateAc, presentation.acText, palette, if (presentation.acOn == null) Tone.MUTED else Tone.NORMAL)
        tintText(root, R.id.seatDriverText, presentation.driverSeat, palette, presentation.driverTone)
        tintText(root, R.id.seatPassengerText, presentation.passengerSeat, palette, presentation.passengerTone)
        tintIcon(root, R.id.seatDriverIcon, palette, presentation.driverTone)
        tintIcon(root, R.id.seatPassengerIcon, palette, presentation.passengerTone)

        val lockIcon = root.findViewById<ImageView>(R.id.lockIcon)
        lockIcon.setImageResource(
            if (presentation.locked == false) R.drawable.ic_lock_open else R.drawable.ic_lock,
        )
        val lockTone = when (presentation.locked) {
            null -> Tone.MUTED
            true -> Tone.OK
            false -> Tone.WARN
        }
        lockIcon.imageTintList = ColorStateList.valueOf(color(palette, lockTone))
        tintText(root, R.id.lockText, presentation.lockText, palette, lockTone)
        tintText(root, R.id.doorsText, presentation.doorsText, palette, presentation.doorsTone)
        tintIcon(root, R.id.doorsIcon, palette, presentation.doorsTone)
        tintText(root, R.id.windowsText, presentation.windowsText, palette, presentation.windowsTone)
        tintIcon(root, R.id.windowsIcon, palette, presentation.windowsTone)
        tintText(root, R.id.sunroofText, presentation.sunroofText, palette, presentation.sunroofTone)
        tintIcon(root, R.id.sunroofIcon, palette, presentation.sunroofTone)

        root.findViewById<TireDiagramView>(R.id.tireDiagram).apply {
            bodyColor = if (palette.dark) 0xFFC5CED8.toInt() else 0xFF9AA3AE.toInt()
            cabinColor = if (palette.dark) 0xFF1A2030.toInt() else 0xFF2C3440.toInt()
            textColor = palette.text1
            warnColor = palette.warn
            mutedColor = palette.text2
            bind(presentation.tires)
        }

        root.findViewById<View>(R.id.carArt).contentDescription = presentation.summary
        root.findViewById<View>(R.id.cardBattery).contentDescription =
            if (presentation.sample) "Energia, exemplo, ${presentation.socText}" else "Energia, ${presentation.socText}"
    }

    private fun tintText(root: View, id: Int, value: String, palette: Palette, tone: Tone) {
        root.findViewById<TextView>(id).apply {
            text = value
            setTextColor(color(palette, tone))
        }
    }

    private fun tintIcon(root: View, id: Int, palette: Palette, tone: Tone) {
        root.findViewById<ImageView>(id).imageTintList = ColorStateList.valueOf(color(palette, tone))
    }

    private fun color(palette: Palette, tone: Tone): Int = when (tone) {
        Tone.OK -> palette.ok
        Tone.WARN -> palette.warn
        Tone.DANGER -> palette.danger
        Tone.ACCENT -> palette.accent
        Tone.MUTED -> palette.text2
        Tone.NORMAL -> palette.text1
    }
}
