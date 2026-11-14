package com.eymen.kurulum

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.Normalizer
import java.util.concurrent.Executors

internal object KumandaStil {
    const val zemin = "#070B16"
    const val kart = "#101A33"
    const val altin = "#E8C27A"
    const val yazi = "#F2F4F8"
    const val ikincil = "#A3ACBF"
    fun dp(a: Activity, v: Int) = (a.resources.displayMetrics.density * v).toInt()

    fun bg(a: Activity, color: String, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(a,radius).toFloat()
            setStroke(dp(a,1),Color.parseColor("#26324F"))
        }

    fun label(a: Activity, value: String, size: Float = 16f,
              color: String = yazi, bold: Boolean = false): TextView =
        TextView(a).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor(color))
            gravity = Gravity.CENTER_VERTICAL
            if(bold) setTypeface(typeface,Typeface.BOLD)
        }

    fun button(a: Activity, value: String, primary: Boolean = false,
               height: Int = 50, action: () -> Unit): TextView =
        label(a,value,16f,if(primary) "#1E1505" else yazi,true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(a,height)
            background = bg(a,if(primary) altin else kart,12)
            setOnClickListener { action() }
        }

    fun column(a: Activity): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
    }
    fun add(a: Activity, parent: LinearLayout, child: View, bottom: Int = 10) {
        parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply {
            bottomMargin = dp(a,bottom)
        })
    }
}

class KumandaEkrani(
    private val act: Activity, private val tv: Tv,
    private val geri: () -> Unit, private val yedegeGec: () -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var bagli = false
    private lateinit var status: TextView
    private val controls = ArrayList<View>()
    private var repeating: Runnable? = null

    private fun disable() {
        bagli = false
        status.text = "Bağlı değil · Komut merkezinden bağlan"
        for(button in controls) { button.isEnabled = false; button.alpha = 0.4f }
    }

    private fun connect() {
        executor.execute {
            val available = try { tv.ip.isNotBlank() && tv.komut("echo EYMEN").contains("EYMEN") }
                catch (_: Throwable) { false }
            act.runOnUiThread {
                if(available) {
                    bagli = true
                    status.text = tv.ip + " bağlı"
                    for(button in controls) { button.isEnabled = true; button.alpha = 1f }
                } else disable()
            }
        }
    }

    private fun send(code: Int) {
        if(!bagli) return
        executor.execute {
            try { tv.komut("input keyevent " + code) }
            catch (_: Throwable) { act.runOnUiThread { disable() } }
        }
    }

    private fun control(button: TextView, code: Int, repeat: Boolean = false): TextView {
        controls.add(button)
        button.alpha = 0.4f
        button.isEnabled = false
        if(repeat) {
            button.setOnTouchListener { v, event ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        send(code)
                        repeating = object : Runnable {
                            override fun run() {
                                if(bagli) { send(code); handler.postDelayed(this,150L) }
                            }
                        }
                        handler.postDelayed(repeating!!,150L)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        repeating?.let { handler.removeCallbacks(it) }
                        repeating = null
                        v.performClick()
                        true
                    }
                    else -> true
                }
            }
        } else button.setOnClickListener { send(code) }
        return button
    }

    private fun row(pad: LinearLayout, keys: List<Pair<String, Int>>, repeated: Set<Int> = emptySet()) {
        val line = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        for((name,code) in keys) {
            val button = control(KumandaStil.button(act,name,height=54) {},code,code in repeated)
            line.addView(button,LinearLayout.LayoutParams(0,KumandaStil.dp(act,54),1f).apply {
                marginEnd = KumandaStil.dp(act,8)
            })
        }
        KumandaStil.add(act,pad,line,8)
    }

    private fun shellText(src: String): String = Normalizer.normalize(src,Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"")
        .replace('ı','i').replace('İ','I')
        .replace(" ","%s")
        .replace("'","'\\''")

    fun gorunum(): View {
        val a = act
        val scroll = ScrollView(a).apply {
            setBackgroundColor(Color.parseColor(KumandaStil.zemin))
            fitsSystemWindows = true
        }
        val base = KumandaStil.column(a).apply {
            setPadding(KumandaStil.dp(a,18),KumandaStil.dp(a,24),
                KumandaStil.dp(a,18),KumandaStil.dp(a,30))
        }
        scroll.addView(base)
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(KumandaStil.button(a,"‹",height=42) { geri() },
            LinearLayout.LayoutParams(KumandaStil.dp(a,54),-2))
        head.addView(KumandaStil.label(a,"Kumanda",25f, KumandaStil.yazi,true),
            LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = KumandaStil.dp(a,16) })
        KumandaStil.add(a,base,head,6)
        status = KumandaStil.label(a,"Bağlı değil · Komut merkezinden bağlan",14f,KumandaStil.ikincil)
        KumandaStil.add(a,base,status,16)

        val tabs = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(KumandaStil.button(a,"Kumanda",true) {},LinearLayout.LayoutParams(0,-2,1f))
        tabs.addView(KumandaStil.button(a,"Yedek") { yedegeGec() },
            LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = KumandaStil.dp(a,8) })
        KumandaStil.add(a,base,tabs,18)

        val pad = KumandaStil.column(a).apply {
            setPadding(KumandaStil.dp(a,14),KumandaStil.dp(a,14),
                KumandaStil.dp(a,14),KumandaStil.dp(a,14))
            background = KumandaStil.bg(a,KumandaStil.kart,20)
        }
        row(pad,listOf("Ana ekran" to 3,"Menü" to 82,"Mavi ⚡" to 186))
        row(pad,listOf(" " to 0,"▲" to 19," " to 0),setOf(19))
        row(pad,listOf("◀" to 21,"OK" to 23,"▶" to 22),setOf(21,22))
        row(pad,listOf("Geri" to 4,"▼" to 20,"Kapat" to 223),setOf(20))
        row(pad,listOf("Ses −" to 25,"Sessiz" to 164,"Ses +" to 24),setOf(24,25))
        row(pad,listOf("Kanal −" to 167,"Mavi" to 186,"Kanal +" to 166))
        row(pad,listOf("Kırmızı" to 183,"Yeşil" to 184,"Sarı" to 185))
        KumandaStil.add(a,base,pad,18)

        KumandaStil.add(a,base,KumandaStil.label(a,"TV'YE YAZ",14f,KumandaStil.altin,true),8)
        val text = EditText(a).apply {
            hint = "TV'ye metin gönder"
            setHintTextColor(Color.parseColor(KumandaStil.ikincil))
            setTextColor(Color.parseColor(KumandaStil.yazi))
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = KumandaStil.bg(a,KumandaStil.kart)
            setPadding(KumandaStil.dp(a,15),KumandaStil.dp(a,12),
                KumandaStil.dp(a,15),KumandaStil.dp(a,12))
        }
        KumandaStil.add(a,base,text,6)
        text.setOnEditorActionListener { _,action,_ ->
            if(action == EditorInfo.IME_ACTION_SEND) {
                val content = text.text.toString()
                if(content.isNotBlank() && bagli) {
                    executor.execute {
                        try { tv.komut("input text '" + shellText(content) + "'") }
                        catch (_: Throwable) { act.runOnUiThread { disable() } }
                    }
                }
                true
            } else false
        }
        KumandaStil.add(a,base,KumandaStil.label(a,
            "Türkçe harfler sade harfe çevrilir",12f,KumandaStil.ikincil),8)
        connect()
        return scroll
    }

    fun kapat() {
        repeating?.let { handler.removeCallbacks(it) }
        repeating = null
        executor.shutdownNow()
    }
}
