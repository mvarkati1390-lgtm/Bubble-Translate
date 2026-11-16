/*
 ══════════ Bubble Translate v4.1 — همه کد در یک فایل (اندروید ۱۰ به بالا) ══════════

 رفتار حباب (v4.1): هیچ ترجمه‌ی خودکاری وجود ندارد.
  • لمس حباب (آبی → نارنجی → سبز) = فقط همین صفحه‌ی لحظه‌ی لمس، یک بار ترجمه می‌شود
  • لمس دوباره (سبز → آبی) = ترجمه خاموش و پاک می‌شود؛ اگر چیزی ترجمه نشود، حباب خودش خاموش برمی‌گردد

 تازه‌ها در v4:
  • انگلیسی هم ترجمه می‌شود (با مدل آفلاین ML Kit) و فینگلیش هم به فارسی درمی‌آید
  • اسم‌ها و هر کلمه‌ی لاتینی که ترجمه نشود، با حروف فارسی نوشته می‌شود
  • ترجمه‌ی کل صفحه (گزینه‌ی «کل صفحه»)، سه حالت خط‌به‌خط / پاراگرافی / زیرنویس واقعاً کار می‌کنند
  • نگه داشتن حباب: 🔄 ترجمه‌ی مجدد، ⏺ ضبط، ✕ خروج
  • چرخش صفحه (افقی/عمودی) پشتیبانی می‌شود

 راه‌اندازی:
 ۱) Android Studio → New Project → Empty Activity (Kotlin)، پکیج: com.example.bubbletranslate
 ۲) کل این فایل را جای MainActivity.kt بگذار (همین یک فایل، تمام کد برنامه است)
 ۳) بخش MANIFEST را در AndroidManifest.xml بگذار
 ۴) بخش GRADLE را در app/build.gradle.kts اضافه کن، Sync و Run
 ۵) داخل برنامه: «۱) دانلود مدل‌ها» را یک بار با اینترنت بزن؛ بعدش آفلاین کار می‌کند

 ──────── MANIFEST ────────
 <manifest xmlns:android="http://schemas.android.com/apk/res/android">
   <uses-permission android:name="android.permission.INTERNET"/>
   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
   <uses-permission android:name="android.permission.RECORD_AUDIO"/>
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
   <application android:label="Bubble Translate"
       android:theme="@android:style/Theme.Material.Light.NoActionBar">
     <activity android:name=".MainActivity" android:exported="true">
       <intent-filter>
         <action android:name="android.intent.action.MAIN"/>
         <category android:name="android.intent.category.LAUNCHER"/>
       </intent-filter>
     </activity>
     <service android:name=".TranslateService" android:exported="false"
         android:foregroundServiceType="mediaProjection|microphone"/>
   </application>
 </manifest>

 ──────── GRADLE (app/build.gradle.kts) ────────
 android { compileSdk = 34
   defaultConfig { minSdk = 29; targetSdk = 34 }
   compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
   kotlinOptions { jvmTarget = "17" } }
 dependencies {
   implementation("com.google.mlkit:translate:17.0.3")
   implementation("com.google.mlkit:text-recognition:16.0.1")
   implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
   implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
   implementation("com.google.mlkit:text-recognition-korean:16.0.1")
   implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
 }
*/
package com.example.bubbletranslate

import android.Manifest
import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

// ───────────── زبان‌ها ─────────────
object Langs {
    const val AUTO = "auto"   // انگلیسی + فینگلیش، تشخیص خودکار برای هر خط
    const val FGL = "fgl"     // فقط فینگلیش → فارسی (کاملاً آفلاین، بدون مدل)

    // OCR آفلاین گوگل فارسی/عربی/روسی را نمی‌خواند؛ پس مبدأ فقط این‌هاست
    val src = linkedMapOf(
        "English + فینگلیش (خودکار)" to AUTO, "فقط فینگلیش → فارسی" to FGL,
        "English" to TranslateLanguage.ENGLISH, "Français" to TranslateLanguage.FRENCH,
        "Deutsch" to TranslateLanguage.GERMAN, "Español" to TranslateLanguage.SPANISH,
        "Italiano" to TranslateLanguage.ITALIAN, "Türkçe" to TranslateLanguage.TURKISH,
        "中文" to TranslateLanguage.CHINESE, "日本語" to TranslateLanguage.JAPANESE,
        "한국어" to TranslateLanguage.KOREAN, "हिन्दी" to TranslateLanguage.HINDI
    )
    val dst = linkedMapOf(
        "فارسی" to TranslateLanguage.PERSIAN, "English" to TranslateLanguage.ENGLISH,
        "العربية" to TranslateLanguage.ARABIC, "Türkçe" to TranslateLanguage.TURKISH,
        "Русский" to TranslateLanguage.RUSSIAN, "Français" to TranslateLanguage.FRENCH,
        "Deutsch" to TranslateLanguage.GERMAN, "Español" to TranslateLanguage.SPANISH
    )

    fun engOf(s: String) = if (s == AUTO || s == FGL) TranslateLanguage.ENGLISH else s

    fun recognizer(l: String): TextRecognizer = when (l) {
        TranslateLanguage.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        TranslateLanguage.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        TranslateLanguage.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        TranslateLanguage.HINDI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // جفت‌مدل‌هایی که باید یک بار دانلود شوند (هم دانلود در برنامه، هم سرویس از همین استفاده می‌کند)
    //  ۱) متن انگلیسی/خارجی → مقصد   ۲) فینگلیش (که اول فارسی می‌شود) → مقصد، اگر مقصد فارسی نباشد
    fun modelPairs(srcRaw: String, dst: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val eng = engOf(srcRaw)
        if (srcRaw != FGL && eng != dst) out.add(eng to dst)
        if ((srcRaw == AUTO || srcRaw == FGL) && dst != TranslateLanguage.PERSIAN) out.add(TranslateLanguage.PERSIAN to dst)
        return out
    }
}

// ───────────── فینگلیش → فارسی + نوشتن اسم‌های لاتین با حروف فارسی (بدون اینترنت) ─────────────
object Finglish {
    // «کلید معنی|کلید معنی|…» — برای افزودن کلمه یا اسم، همین‌جا یک مورد اضافه کن
    private val dict: Map<String, String> = (
        "salam سلام|khobi خوبی|khoob خوب|khobe خوبه|mersi مرسی|merci مرسی|mamnoon ممنون|mamnun ممنون|" +
        "chetori چطوری|chetor چطور|chetore چطوره|khodafez خداحافظ|khoda خدا|bale بله|are آره|ara آره|na نه|" +
        "man من|to تو|shoma شما|ma ما|oon اون|un اون|in این|ke که|ye یه|yek یک|ya یا|va و|ba با|bara برای|" +
        "baraye برای|az از|be به|dar در|ta تا|ham هم|hame همه|hich هیچ|chi چی|che چه|chera چرا|kay کی|ki کی|" +
        "koja کجا|kojai کجایی|kojaei کجایی|chand چند|cheghadr چقدر|alan الان|emrooz امروز|farda فردا|" +
        "dirooz دیروز|shab شب|rooz روز|sobh صبح|nemidoonam نمیدونم|midoonam میدونم|nemikham نمیخوام|" +
        "mikham میخوام|mikhay میخوای|miram میرم|miri میری|miad میاد|mishe میشه|nemishe نمیشه|bashe باشه|" +
        "nabashe نباشه|hast هست|nist نیست|hastam هستم|hasti هستی|dari داری|daram دارم|nadaram ندارم|" +
        "dare داره|nadare نداره|bood بود|boodam بودم|shod شد|kardam کردم|kardi کردی|kard کرد|kon کن|" +
        "bokon بکن|boro برو|biya بیا|bia بیا|bebakhshid ببخشید|bebakhshin ببخشین|lotfan لطفا|dige دیگه|" +
        "hala حالا|kheyli خیلی|kheili خیلی|bad بد|khoshgel خوشگل|agar اگر|age اگه|vali ولی|amma اما|" +
        "chon چون|chonke چون که|pas پس|hatman حتما|albate البته|fagat فقط|faghat فقط|doost دوست|" +
        "dooset دوستت|aziz عزیز|azizam عزیزم|joon جون|joonam جونم|dadash داداش|dadasham داداشم|" +
        "baradar برادر|kar کار|kari کاری|bazi بازی|bazikon بازیکن|pool پول|pol پول|mashin ماشین|" +
        "khone خونه|khane خانه|ghabl قبل|baad بعد|inja اینجا|onja اونجا|hamin همین|hamoon همون|" +
        "oke اوکی|nakhair نخیر|salamati سلامتی|sharmande شرمنده|ghorbanet قربانت|sabr صبر|bebin ببین|bego بگو|" +
        "join جوین|find فیند|event ایونت|clan کلن|player پلیر|players پلیرها|server سرور|admin ادمین|" +
        // ── فعل‌ها و کلمه‌های رایج بیشتر ──
        "khastam خواستم|goftam گفتم|goft گفت|didam دیدم|did دید|raftam رفتم|raft رفت|oomad اومد|umad اومد|" +
        "oomadam اومدم|mikonam میکنم|mikoni میکنی|mikone میکنه|nemikonam نمیکنم|nemikoni نمیکنی|nemikone نمیکنه|" +
        "chikar چیکار|chekar چیکار|chizi چیزی|chiz چیز|kasi کسی|hichkas هیچکس|hamishe همیشه|hichvaght هیچوقت|" +
        "vaghti وقتی|vaght وقت|zood زود|deer دیر|saat ساعت|daghighe دقیقه|hafte هفته|mah ماه|sal سال|" +
        "baba بابا|mamman مامان|madar مادر|pedar پدر|khahar خواهر|ghashang قشنگ|jaleb جالب|ajib عجیب|" +
        "khoshhal خوشحال|narahat ناراحت|asabani عصبانی|kam کم|ziad زیاد|bishtar بیشتر|kamtar کمتر|behtar بهتر|" +
        "bozorg بزرگ|kochik کوچیک|jadid جدید|ghadimi قدیمی|rast راست|chap چپ|bala بالا|paein پایین|jolo جلو|" +
        "aghab عقب|ghabool قبول|laghv لغو|baz باز|baste بسته|shoroo شروع|payan پایان|ersal ارسال|" +
        "daryaft دریافت|tamam تمام|tamoom تموم|bashi باشی|bede بده|bedeh بده|begir بگیر|bezan بزن|bezar بذار|" +
        "nazar نظر|jang جنگ|" +
        // ── شمارش ──
        "do دو|se سه|chahar چهار|panj پنج|shesh شش|haft هفت|hasht هشت|noh نه|dah ده|sad صد|hezar هزار|" +
        // ── اسم‌های رایج ──
        "ali علی|ahmad احمد|mohammad محمد|mamad ممد|reza رضا|hossein حسین|hosein حسین|hasan حسن|mehdi مهدی|" +
        "mahdi مهدی|amir امیر|amin امین|sina سینا|arash آرش|babak بابک|kian کیان|sara سارا|maryam مریم|" +
        "zahra زهرا|fateme فاطمه|negar نگار|nima نیما|payam پیام|saeed سعید|majid مجید|hamid حمید|" +
        "farhad فرهاد|kaveh کاوه|parsa پارسا|behnam بهنام|shahab شهاب|ehsan احسان|milad میلاد|soheil سهیل|" +
        "omid امید|navid نوید|pouya پویا|peyman پیمان|kamran کامران|farzad فرزاد|siavash سیاوش|shayan شایان|" +
        "arman آرمان|alireza علیرضا|mohammadreza محمدرضا|hamidreza حمیدرضا|" +
        // ── بازی و اینترنت ──
        "level لول|score اسکور|kill کیل|team تیم|chat چت|shop شاپ|quest کوئست|boss باس|map مپ|home هوم|" +
        "start استارت|stop استاپ|play پلی|gold گلد|coin کوین|coins کوین|gem جم|gems جم|bonus بونوس|" +
        "online آنلاین|offline آفلاین|ping پینگ|wiki ویکی|forum فروم|discord دیسکورد|telegram تلگرام|" +
        "instagram اینستاگرام|youtube یوتیوب|google گوگل|android اندروید|iphone آیفون|windows ویندوز"
        ).split("|").mapNotNull { e ->
            e.trim().split(" ", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()

    // کلمات مخصوص فینگلیش برای تشخیص (کلماتی که انگلیسی هم هستند مثل to/in/man عمداً نیستند)
    private val strong = setOf(
        "salam", "khobi", "khoob", "khobe", "mersi", "mamnoon", "chetori", "chetor", "khodafez", "bale",
        "nemidoonam", "midoonam", "nemikham", "mikham", "miram", "mishe", "bashe", "hast", "nist", "daram",
        "dare", "dari", "kheyli", "kheili", "chi", "che", "chera", "koja", "kojai", "alan", "emrooz", "farda",
        "hala", "dige", "vali", "chon", "hatman", "albate", "fagat", "faghat", "lotfan", "doost", "azizam",
        "joon", "dadash", "bebakhshid", "ke", "ye", "va", "baraye", "bara", "az", "hamin", "hamoon", "inja",
        "onja", "oon", "ghabl", "baad", "khone", "khane", "nakhair", "oke", "bia", "biya", "boro",
        "khastam", "goftam", "raftam", "oomad", "umad", "oomadam", "mikonam", "mikoni", "mikone", "nemikonam",
        "nemikoni", "nemikone", "chikar", "chekar", "chizi", "hamishe", "hichvaght", "vaghti", "khoshhal",
        "narahat", "asabani", "ghashang", "jaleb", "ajib", "bashi", "bebin", "bego", "joonam", "khoshgel",
        "sharmande", "ghorbanet", "aziz", "mamnun", "jolo", "aghab", "ghabool", "tamoom", "bede", "bezan",
        "bezar", "mikhay", "dooset", "dadasham"
    )
    private val en = setOf(
        "the", "is", "are", "you", "your", "for", "with", "and", "of", "that", "this", "have", "has", "can",
        "now", "will", "was", "were", "not", "all", "there", "use", "it", "on", "at", "by", "from", "only",
        "wait", "join", "event", "players", "enabled", "disabled", "earned", "its", "if", "when", "how",
        "what", "i", "my", "we", "he", "she", "they", "do", "does", "did", "a"
    )
    private val di = mapOf("kh" to "خ", "sh" to "ش", "ch" to "چ", "zh" to "ژ", "gh" to "ق",
        "oo" to "و", "ou" to "و", "ee" to "ی", "aa" to "ا")
    // ترکیب‌های مخصوص اسم‌ها و کلمات انگلیسی
    private val diEn = mapOf("th" to "ت", "ph" to "ف", "ck" to "ک", "qu" to "کو")
    private val sg = mapOf('b' to "ب", 'p' to "پ", 't' to "ت", 'j' to "ج", 'd' to "د", 'r' to "ر", 'z' to "ز",
        's' to "س", 'f' to "ف", 'q' to "ق", 'k' to "ک", 'g' to "گ", 'l' to "ل", 'm' to "م", 'n' to "ن",
        'v' to "و", 'w' to "و", 'h' to "ه", 'x' to "کس", 'c' to "ک")
    private val wordRe = Regex("[A-Za-z']+")
    private val camel = Regex("(?<=[a-z])(?=[A-Z])")   // AliKing → Ali King

    // آیا این خط فینگلیش است؟ (در غیر این صورت انگلیسی فرض می‌شود)
    fun isFinglish(t: String): Boolean {
        var f = 0; var e = 0
        for (m in wordRe.findAll(t)) {
            val w = m.value.lowercase()
            if (w in strong || w.contains("kh") || w.endsWith("oon")) f++
            if (w in en) e++
        }
        return f > 0 && f >= e
    }

    // all = true → همه‌چیز (اسم بازیکن، تگ، دستور) هم به حروف فارسی درمی‌آید
    // english = true → برای باقی‌مانده‌ی کلمات لاتین بعد از ترجمه (اسم‌ها): e آخر کلمه خوانده نمی‌شود و …
    fun toPersian(t: String, all: Boolean = true, english: Boolean = false): String {
        val sb = StringBuilder()
        var last = 0
        for (m in wordRe.findAll(t)) {
            sb.append(t, last, m.range.first)
            val w = m.value
            val prev = if (m.range.first > 0) t[m.range.first - 1] else ' '
            val next = if (m.range.last + 1 < t.length) t[m.range.last + 1] else ' '
            val skip = !all && (prev == '/' || prev == '[' || prev == ']' || next == ':' || next == ']' ||
                prev.isDigit() || next.isDigit() || (w.length > 1 && w.drop(1).any { it.isUpperCase() }))
            sb.append(if (skip) w else conv(w, english))
            last = m.range.last + 1
        }
        sb.append(t, last, t.length)
        val s = sb.toString()
        return if (english) s else s.replace('?', '؟').replace(',', '،')
    }

    private fun conv(w0: String, en: Boolean): String =
        w0.split(camel).joinToString(" ") { convWord(it, en) }

    private fun convWord(w0: String, en: Boolean): String {
        val w = w0.lowercase()
        dict[w]?.let { return it }
        val sb = StringBuilder()
        var i = 0
        while (i < w.length) {
            if (i + 1 < w.length) {
                val d = w.substring(i, i + 2)
                val r = di[d] ?: (if (en) diEn[d] else null)
                if (r != null) { sb.append(if (d == "aa" && i == 0) "آ" else r); i += 2; continue }
            }
            val c = w[i]
            // حرف بی‌صدای تکراری (ll, ss, tt …) در اسم‌های انگلیسی یکی نوشته می‌شود
            if (en && i > 0 && c == w[i - 1] && c !in "aeiou") { i++; continue }
            val first = i == 0
            val last = i == w.length - 1
            val nextC = if (i + 1 < w.length) w[i + 1] else ' '
            sb.append(when (c) {
                'a' -> if (first || last) "ا" else ""
                'e' -> if (first) "ا" else if (last) (if (en && w.length > 2) "" else "ه") else ""
                'o' -> if (first) "ا" else if (last) "و" else ""
                'i' -> if (first) "ای" else "ی"
                'u' -> "و"
                'y' -> "ی"
                'c' -> if (en && (nextC == 'e' || nextC == 'i' || nextC == 'y')) "س" else "ک"
                '\'' -> if (en) "" else "ع"
                else -> sg[c] ?: c.toString()
            })
            i++
        }
        return sb.toString()
    }
}

// ───────────── صفحه اصلی ─────────────
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var srcSp: Spinner
    private lateinit var dstSp: Spinner
    private lateinit var rg: RadioGroup
    private lateinit var allCb: CheckBox
    private lateinit var wholeCb: CheckBox
    private var captureInternal = true
    private var captureMic = true
    private lateinit var internalAudioButton: Button
    private lateinit var micAudioButton: Button
    private val cfg by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }
    private val srcKeys = Langs.src.keys.toList()
    private val dstKeys = Langs.dst.keys.toList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val miss = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (miss.isNotEmpty()) requestPermissions(miss.toTypedArray(), 1)

        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }
        fun label(t: String) = root.addView(TextView(this).apply { text = t; textSize = 16f; setPadding(0, (14 * d).toInt(), 0, 4) })
        fun button(t: String, f: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { f() } })

        label("زبان متن روی صفحه (مبدأ)")
        srcSp = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, srcKeys)
            setSelection(srcKeys.indexOfFirst { Langs.src[it] == cfg.getString("src", Langs.AUTO) }.coerceAtLeast(0))
        }
        root.addView(srcSp)
        label("ترجمه به (مقصد)")
        dstSp = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, dstKeys)
            setSelection(dstKeys.indexOfFirst { Langs.dst[it] == cfg.getString("dst", TranslateLanguage.PERSIAN) }.coerceAtLeast(0))
        }
        root.addView(dstSp)
        label("حالت ترجمه")
        rg = RadioGroup(this)
        fun rb(rid: Int, t: String) = rg.addView(RadioButton(this).apply { text = t; this.id = rid })
        rb(10, "دقیق خط‌به‌خط (مثل Google Lens)")
        rb(11, "پاراگرافی (هر بلوک متن یکجا)")
        rb(12, "زیرنویس ویدیو (پایین صفحه)")
        root.addView(rg)
        rg.check(cfg.getInt("mode", 10))
        allCb = CheckBox(this).apply {
            text = "ترجمه همه‌چیز (اسم‌ها، تگ‌ها و دستورها هم)"
            isChecked = cfg.getBoolean("all", true)
        }
        root.addView(allCb)
        wholeCb = CheckBox(this).apply {
            text = "ترجمه همه‌ی متن‌های همین صفحه (اگر خاموش باشد فقط اطراف حباب)"
            isChecked = cfg.getBoolean("whole", true)
        }
        root.addView(wholeCb)

        button("۱) دانلود مدل‌ها (فقط یک بار، با اینترنت)") { save(); download() }
        button("۲) اجازه نمایش روی برنامه‌ها") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        captureInternal = cfg.getBoolean("audio_internal", true)
        captureMic = cfg.getBoolean("audio_mic", true)
        internalAudioButton = Button(this).apply { setOnClickListener { captureInternal = !captureInternal; refreshAudioButtons(); saveAudio() } }
        micAudioButton = Button(this).apply { setOnClickListener { captureMic = !captureMic; refreshAudioButtons(); saveAudio() } }
        root.addView(internalAudioButton)
        root.addView(micAudioButton)
        refreshAudioButtons()
        button("۳) شروع ترجمه و ضبط هم‌زمان") { start() }
        button("توقف") { stopService(Intent(this, TranslateService::class.java)) }
        status = TextView(this).apply {
            setPadding(0, (16 * d).toInt(), 0, 0)
            text = "بعد از تأیید مجوز صفحه، حباب و ضبط شروع می‌شوند • ترجمه‌ی خودکار وجود ندارد • لمس حباب (آبی) = فقط همین صفحه یک بار ترجمه می‌شود (نارنجی = در حال ترجمه، سبز = روشن) • لمس دوباره = خاموش و پاک شدن ترجمه • نگه داشتن حباب = ترجمه‌ی مجدد / ضبط / خروج"
        }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refreshAudioButtons() {
        internalAudioButton.text = "صدای داخل گوشی: ${if (captureInternal) "روشن" else "خاموش"}"
        micAudioButton.text = "صدای بیرون گوشی (میکروفون): ${if (captureMic) "روشن" else "خاموش"}"
    }

    private fun saveAudio() {
        cfg.edit().putBoolean("audio_internal", captureInternal).putBoolean("audio_mic", captureMic).apply()
    }

    private fun save() {
        saveAudio()
        cfg.edit()
            .putString("src", Langs.src[srcKeys[srcSp.selectedItemPosition]])
            .putString("dst", Langs.dst[dstKeys[dstSp.selectedItemPosition]])
            .putInt("mode", rg.checkedRadioButtonId)
            .putBoolean("all", allCb.isChecked)
            .putBoolean("whole", wholeCb.isChecked).apply()
    }

    // مدل‌های لازم (متن خارجی → مقصد، و برای فینگلیش: فارسی → مقصد) یک‌جا دانلود می‌شوند
    private fun download() {
        val raw = Langs.src[srcKeys[srcSp.selectedItemPosition]]!!
        val t = Langs.dst[dstKeys[dstSp.selectedItemPosition]]!!
        val pairs = Langs.modelPairs(raw, t)
        if (pairs.isEmpty()) { status.text = "این ترکیب نیاز به دانلود ندارد و همیشه آفلاین است"; return }
        status.text = "در حال دانلود مدل‌ها…"
        var left = pairs.size
        var err: String? = null
        for ((s, dd) in pairs) {
            val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(dd).build())
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnFailureListener { err = it.message }
                .addOnCompleteListener {
                    tr.close()
                    left--
                    if (left == 0) {
                        status.text = if (err == null) "✓ دانلود شد؛ از این به بعد بدون اینترنت کار می‌کند"
                        else "خطا در دانلود: $err"
                    }
                }
        }
    }

    private fun start() {
        save()
        if (!Settings.canDrawOverlays(this)) { status.text = "اول اجازه نمایش روی برنامه‌ها را بده"; return }
        if ((captureMic || captureInternal) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "برای ضبط صدای بیرون گوشی، اجازه میکروفون را بده؛ یا دکمه صدای بیرون را خاموش کن."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 7)
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == 7 && res == RESULT_OK && d != null) {
            startForegroundService(Intent(this, TranslateService::class.java).putExtra("code", res).putExtra("data", d))
            moveTaskToBack(true)
        }
    }
}

// ───────────── لایه ترجمه روی صفحه ─────────────
class Item(val box: RectF, val bg: Int, val layout: StaticLayout) {
    // هم روی صفحه و هم داخل ویدیوی ضبط‌شده با همین تابع کشیده می‌شود
    fun draw(cv: Canvas, p: Paint) {
        p.color = bg
        cv.drawRoundRect(box, 8f, 8f, p)                 // متن اصلی را می‌پوشاند
        cv.save()
        cv.translate(box.left, box.top + (box.height() - layout.height) / 2f)
        layout.draw(cv)                                    // متن ترجمه، دقیقاً روی همان خط
        cv.restore()
    }
}

// یک تکه متن خوانده‌شده از صفحه: کادر + متن + تعداد خط‌های اصلی
class Seg(val box: RectF, val text: String, val lines: Int)

class OverlayView(c: Context) : View(c) {
    @Volatile var items: List<Item> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun show(l: List<Item>) { items = l; invalidate() }
    override fun onDraw(cv: Canvas) { for (i in items) i.draw(cv, p) }
}

// حباب شناور (بدون متن تا OCR آن را نخواند)؛ آبی = خاموش، نارنجی = در حال ترجمه‌ی همین صفحه،
// سبز = ترجمه‌ی همین صفحه روشن است، حلقه قرمز = در حال ضبط
class Bubble(c: Context) : View(c) {
    var on = false
    var busy = false
    var rec = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val r = width / 2f
        val col = when {
            busy -> 0xFFEF6C00.toInt()
            on -> 0xFF2E7D32.toInt()
            else -> 0xFF1565C0.toInt()
        }
        p.color = if (rec) 0xFFC62828.toInt() else col; cv.drawCircle(r, r, r, p)
        p.color = Color.WHITE; cv.drawCircle(r, r, r * 0.6f, p)
        p.color = col; cv.drawCircle(r, r, r * 0.3f, p)
    }
}

// ───────────── ضبط ویدیوی صفحه (با ترجمه) + صدای داخلی و میکروفون ─────────────
class Recorder(ctx: Context, proj: MediaProjection, srcW: Int, srcH: Int, private val captureInternal: Boolean, private val captureMic: Boolean) {
    private val sc = minOf(1f, 1920f / maxOf(srcW, srcH))
    private val vw = (srcW * sc).toInt() / 2 * 2
    private val vh = (srcH * sc).toInt() / 2 * 2
    private val dst = Rect(0, 0, vw, vh)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cr = ctx.contentResolver
    private val t0 = System.nanoTime()
    private val vth = HandlerThread("venc").apply { start() }
    private val uri: Uri
    private val pfd: ParcelFileDescriptor
    private val muxer: MediaMuxer
    private val venc: MediaCodec
    private val surface: Surface
    private var aenc: MediaCodec? = null
    private var sys: AudioRecord? = null
    private var mic: AudioRecord? = null
    private var athread: Thread? = null
    private var want = 1
    private var tracks = 0
    private var started = false
    private var vTrack = -1
    private var aTrack = -1
    private var vBase = -1L
    @Volatile private var vStartUs = -1L
    @Volatile private var running = true
    @Volatile var lastMs = 0L
    private val pend = ArrayList<Triple<Int, ByteArray, MediaCodec.BufferInfo>>()

    init {
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "BubbleTranslate_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BubbleTranslate")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)!!
        pfd = cr.openFileDescriptor(uri, "rw")!!
        muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vw, vh).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        venc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        venc.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(c: MediaCodec, i: Int) {}
            override fun onError(c: MediaCodec, e: MediaCodec.CodecException) {}
            override fun onOutputFormatChanged(c: MediaCodec, f: MediaFormat) { vTrack = addTrack(f) }
            override fun onOutputBufferAvailable(c: MediaCodec, i: Int, info: MediaCodec.BufferInfo) {
                try {
                    val bb = c.getOutputBuffer(i)
                    if (bb != null && info.size > 0) {
                        if (vBase < 0) vBase = info.presentationTimeUs
                        info.presentationTimeUs = info.presentationTimeUs - vBase + maxOf(vStartUs, 0L)
                        write(vTrack, bb, info)
                    }
                } finally { c.releaseOutputBuffer(i, false) }
            }
        }, Handler(vth.looper))
        venc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = venc.createInputSurface()
        setupAudio(proj)      // قبل از start تا تعداد ترک‌ها مشخص باشد
        venc.start()
    }

    private fun setupAudio(proj: MediaProjection) {
        if (!captureInternal && !captureMic) return
        val rate = 44100
        val ch = AudioFormat.CHANNEL_IN_MONO
        val enc = AudioFormat.ENCODING_PCM_16BIT
        val buf = maxOf(AudioRecord.getMinBufferSize(rate, ch, enc), 8192) * 2
        val fmt = AudioFormat.Builder().setEncoding(enc).setSampleRate(rate).setChannelMask(ch).build()
        if (captureInternal) try {   // صدای داخلی گوشی (بازی، ویدیو، موزیک)
            val capCfg = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
            sys = AudioRecord.Builder().setAudioFormat(fmt).setAudioPlaybackCaptureConfig(capCfg)
                .setBufferSizeInBytes(buf).build()
        } catch (e: Exception) { sys = null }
        if (captureMic) try {   // صدای بیرون (میکروفون)
            mic = AudioRecord(MediaRecorder.AudioSource.MIC, rate, ch, enc, buf)
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) { mic = null }
        if (sys == null && mic == null) return

        val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val ae = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        ae.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        ae.start()
        aenc = ae
        want = 2
        if (sys != null && runCatching { sys!!.startRecording() }.isFailure) {
            runCatching { sys?.release() }; sys = null
        }
        if (mic != null && runCatching { mic!!.startRecording() }.isFailure) {
            runCatching { mic?.release() }; mic = null
        }
        if (sys == null && mic == null) {
            runCatching { ae.stop(); ae.release() }; aenc = null; want = 1
            return
        }
        athread = Thread { audioLoop(ae, rate) }.also { it.start() }
    }

    private fun audioLoop(ae: MediaCodec, rate: Int) {
        val n = 1024
        val a = ShortArray(n); val b = ShortArray(n)
        val info = MediaCodec.BufferInfo()
        val offUs = (System.nanoTime() - t0) / 1000
        var samples = 0L
        while (running) {
            val na = (sys?.read(a, 0, n) ?: 0).coerceAtLeast(0)
            val nb = (mic?.read(b, 0, n) ?: 0).coerceAtLeast(0)
            val m = maxOf(na, nb)
            if (m == 0) { Thread.sleep(10); continue }
            for (i in 0 until m) {      // مخلوط کردن صدای داخلی و میکروفون
                val x = (if (i < na) a[i].toInt() else 0) + (if (i < nb) b[i].toInt() else 0)
                a[i] = x.coerceIn(-32768, 32767).toShort()
            }
            val idx = ae.dequeueInputBuffer(10_000)
            if (idx >= 0) {
                val bb = ae.getInputBuffer(idx)!!
                bb.clear(); bb.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(a, 0, m)
                ae.queueInputBuffer(idx, 0, m * 2, offUs + samples * 1_000_000L / rate, 0)
            }
            samples += m
            drain(ae, info)
        }
        val idx = ae.dequeueInputBuffer(20_000)
        if (idx >= 0) ae.queueInputBuffer(idx, 0, 0, offUs + samples * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        repeat(20) { drain(ae, info); Thread.sleep(10) }
    }

    private fun drain(ae: MediaCodec, info: MediaCodec.BufferInfo) {
        while (true) {
            val i = ae.dequeueOutputBuffer(info, 0)
            when {
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> aTrack = addTrack(ae.outputFormat)
                i >= 0 -> {
                    val bb = ae.getOutputBuffer(i)
                    if (bb != null) write(aTrack, bb, info)
                    ae.releaseOutputBuffer(i, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
                else -> return
            }
        }
    }

    @Synchronized private fun addTrack(f: MediaFormat): Int {
        val t = muxer.addTrack(f)
        if (++tracks == want) {
            muxer.start(); started = true
            for (p in pend) muxer.writeSampleData(p.first, ByteBuffer.wrap(p.second), p.third)
            pend.clear()
        }
        return t
    }

    @Synchronized private fun write(t: Int, bb: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0 || (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 || t < 0) return
        bb.position(info.offset); bb.limit(info.offset + info.size)
        if (started) muxer.writeSampleData(t, bb, info)
        else {
            val arr = ByteArray(info.size); bb.get(arr)
            pend.add(Triple(t, arr, MediaCodec.BufferInfo().also { it.set(0, info.size, info.presentationTimeUs, info.flags) }))
        }
    }

    // هر فریم صفحه + ترجمه‌ها (حتی وقتی لایه روی صفحه برای OCR لحظه‌ای مخفی است) داخل ویدیو می‌رود
    @Synchronized fun frame(bmp: Bitmap, items: List<Item>) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (!running || now - lastMs < 33) return
            lastMs = now
            if (vStartUs < 0) vStartUs = (System.nanoTime() - t0) / 1000
            val c = surface.lockHardwareCanvas()
            try {
                c.drawBitmap(bmp, null, dst, null)
                c.save(); c.scale(sc, sc)
                for (i in items) i.draw(c, paint)
                c.restore()
            } finally { surface.unlockCanvasAndPost(c) }
        } catch (e: Exception) { }
    }

    fun stop() {
        running = false
        runCatching { sys?.stop() }; runCatching { mic?.stop() }
        runCatching { athread?.join(2500) }
        runCatching { venc.signalEndOfInputStream() }
        Thread.sleep(400)
        runCatching { venc.stop(); venc.release() }
        runCatching { aenc?.stop(); aenc?.release() }
        runCatching { sys?.release() }; runCatching { mic?.release() }
        runCatching { if (started) muxer.stop() }
        runCatching { muxer.release() }
        runCatching { pfd.close() }
        runCatching { cr.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
        vth.quitSafely()
    }
}

// ───────────── سرویس اصلی: ضبط صفحه + OCR + ترجمه ─────────────
class TranslateService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val frameThread = HandlerThread("frames").apply { start() }
    private val lock = Any()
    private lateinit var wm: WindowManager
    private lateinit var proj: MediaProjection
    private lateinit var reader: ImageReader
    private var vd: VirtualDisplay? = null
    private lateinit var overlay: OverlayView
    private lateinit var bubble: Bubble
    private var menu: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleSize = 0
    private var scanJob: Job? = null
    private var translator: Translator? = null   // متن انگلیسی/خارجی → مقصد
    private var trFa: Translator? = null         // فینگلیش (بعد از تبدیل به فارسی) → مقصد، فقط وقتی مقصد فارسی نیست
    private lateinit var recognizer: TextRecognizer
    private var W = 0
    private var H = 0
    private var srcRaw = Langs.AUTO
    private var mode = 10          // 10 = خط‌به‌خط دقیق، 11 = پاراگرافی، 12 = زیرنویس
    private var all = true         // ترجمه همه‌چیز (اسم‌ها و تگ‌ها هم)
    private var whole = true       // کل صفحه، نه فقط اطراف حباب
    private var dstFa = true
    private var pending: Image? = null   // آخرین فریم خام (زیر قفل)
    private var dirty = false
    private var started = false
    private var modelsOk = false
    private var modelErr = false
    private var recW = 0
    private var recH = 0
    @Volatile private var shown: List<Item> = emptyList()   // ترجمه‌ای که باید نمایش داده شود
    @Volatile private var rec: Recorder? = null
    @Volatile private var live = false
    @Volatile private var scanId = 0      // شماره‌ی ترجمه‌ی فعلی؛ ترجمه‌ی قدیمی نباید وضعیت حباب را عوض کند
    private val cache = ConcurrentHashMap<String, String>()
    private val listener = ImageReader.OnImageAvailableListener { r -> onFrame(r) }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i == null) { stopSelf(); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        started = true
        startFg()

        val cfg = getSharedPreferences("cfg", MODE_PRIVATE)
        srcRaw = cfg.getString("src", Langs.AUTO)!!
        val dst = cfg.getString("dst", TranslateLanguage.PERSIAN)!!
        mode = cfg.getInt("mode", 10)
        all = cfg.getBoolean("all", true)
        whole = cfg.getBoolean("whole", true)
        dstFa = dst == TranslateLanguage.PERSIAN
        val eng = Langs.engOf(srcRaw)
        recognizer = Langs.recognizer(eng)
        // انگلیسی (یا زبان خارجی) → مقصد با مدل آفلاین؛ فینگلیش با قاعده به فارسی، و اگر مقصد فارسی نبود فارسی → مقصد
        translator = if (srcRaw != Langs.FGL && eng != dst) newTr(eng, dst) else null
        trFa = if ((srcRaw == Langs.AUTO || srcRaw == Langs.FGL) && dst != TranslateLanguage.PERSIAN)
            newTr(TranslateLanguage.PERSIAN, dst) else null

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        screen().let { W = it.first; H = it.second }

        @Suppress("DEPRECATION") val data = i.getParcelableExtra<Intent>("data")!!
        proj = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(i.getIntExtra("code", 0), data)
        proj.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, main)
        reader = newReader()
        vd = proj.createVirtualDisplay("bt", W, H, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, main)

        overlay = OverlayView(this)
        wm.addView(overlay, lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false))
        addBubble()
        live = false
        bubble.on = false
        bubble.invalidate()
        // بعد از همان یک مجوز صفحه، ضبط هم شروع می‌شود (ترجمه با لمس حباب)
        main.post { startRecordingAutomatically() }
        return START_NOT_STICKY
    }

    private fun newTr(s: String, d: String): Translator =
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(d).build())

    private fun startFg() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("bt", "Bubble Translate", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "bt").setContentTitle("مترجم شناور فعال است")
            .setContentText("حباب را نگه دار: ترجمه مجدد، ضبط ویدیو یا خروج").setSmallIcon(android.R.drawable.ic_menu_search).build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        val micWanted = getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("audio_mic", true)
        if (Build.VERSION.SDK_INT >= 30 && micWanted && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(1, n, type)
    }

    // اندازه واقعی صفحه با در نظر گرفتن چرخش (بازی‌ها معمولاً افقی‌اند)
    private fun screen(): Pair<Int, Int> {
        val m = DisplayMetrics()
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    private fun newReader(): ImageReader =
        ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 4, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE).also {
            it.setOnImageAvailableListener(listener, Handler(frameThread.looper))
        }

    private fun resize(w: Int, h: Int) {
        scanJob?.cancel()
        scanJob = null
        scanId++
        live = false
        W = w; H = h
        synchronized(lock) { pending?.close(); pending = null; dirty = false }
        val old = reader
        reader = newReader()
        vd?.resize(W, H, resources.displayMetrics.densityDpi)
        vd?.surface = reader.surface
        old.close()
        shown = emptyList()
        overlay.show(emptyList())
        bubble.on = false
        bubble.busy = false
        bubble.visibility = View.VISIBLE
        bubble.invalidate()
        bubbleParams?.let { bp ->
            bp.x = bp.x.coerceIn(0, (W - bubbleSize).coerceAtLeast(0))
            bp.y = bp.y.coerceIn(0, (H - bubbleSize).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(bubble, bp) }
        }
    }

    // چرخش صفحه: اندازه‌ی ضبط صفحه با اندازه‌ی جدید هماهنگ می‌شود
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        if (!started || !::overlay.isInitialized || !::bubble.isInitialized) return
        main.postDelayed({
            val (w, h) = screen()
            if (w != W || h != H) resize(w, h)
        }, 500)
    }

    // هر فریم جدید: اگر ضبط روشن است همان‌جا وارد ویدیو می‌شود؛ یک نسخه هم برای OCR نگه داشته می‌شود
    private fun onFrame(r: ImageReader) {
        val img = try { r.acquireLatestImage() } catch (e: Exception) { null } ?: return
        try {
            // Recorder receives its own software bitmap. It must never close or retain
            // the ImageReader buffer that the OCR pipeline still needs.
            val rc = rec
            if (rc != null) {
                val copy = img.hardwareBuffer?.let { hb -> Bitmap.wrapHardwareBuffer(hb, null)?.copy(Bitmap.Config.ARGB_8888, false) }
                if (copy != null) {
                    try {
                        if (copy.width == recW && copy.height == recH) rc.frame(copy, shown)
                    } finally { copy.recycle() }
                }
            }
            synchronized(lock) { pending?.close(); pending = img; dirty = true }
        } catch (e: Exception) { img.close() }
    }

    private fun snapshot(requireDirty: Boolean): Bitmap? = synchronized(lock) { snap(requireDirty) }

    private fun snap(requireDirty: Boolean): Bitmap? {
        val img = pending ?: return null
        if (requireDirty && !dirty) return null
        dirty = false
        return try {
            val hb = img.hardwareBuffer ?: return null
            val hw = Bitmap.wrapHardwareBuffer(hb, null) ?: return null
            hw.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            if (pending === img) pending = null
            img.close()
        }
    }

    private fun lp(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun addBubble() {
        bubble = Bubble(this)
        val s = (56 * resources.displayMetrics.density).toInt()
        val bp = lp(s, s, true).apply { x = 16; y = H / 3 }
        bubbleParams = bp
        bubbleSize = s
        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false; var t0 = 0L
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = bp.x; oy = bp.y; moved = false; t0 = e.eventTime }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx; val dy = e.rawY - sy
                        if (abs(dx) > 12 || abs(dy) > 12) moved = true
                        if (moved) { bp.x = ox + dx.toInt(); bp.y = oy + dy.toInt(); wm.updateViewLayout(v, bp) }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) { if (e.eventTime - t0 > 600) showMenu(bp, s) else toggle() }
                }
                return true
            }
        })
        wm.addView(bubble, bp)
    }

    // منوی کوچک کنار حباب (نگه داشتن حباب): 🔄 ترجمه‌ی مجدد، ⏺/⏹ ضبط ویدیو با صدا، ✕ خروج
    private fun showMenu(bp: WindowManager.LayoutParams, s: Int) {
        hideMenu()
        val d = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(0xEE222222.toInt()); cornerRadius = 24 * d }
        }
        fun btn(t: String, f: () -> Unit) = row.addView(TextView(this).apply {
            text = t; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((52 * d).toInt(), (48 * d).toInt())
            setOnClickListener { hideMenu(); f() }
        })
        btn("🔄") { rescan() }
        btn(if (rec == null) "⏺" else "⏹") { toggleRec() }
        btn("✕") { stopSelf() }
        val mw = (156 * d).toInt()
        val mp = lp(mw, (48 * d).toInt(), true).apply {
            x = if (bp.x > W / 2) bp.x - mw - 8 else bp.x + s + 8
            y = bp.y + s / 2 - (24 * d).toInt()
        }
        wm.addView(row, mp)
        menu = row
        main.postDelayed({ hideMenu() }, 5000)
    }

    private fun hideMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    private fun startRecordingAutomatically() {
        if (rec != null) return
        val cfg = getSharedPreferences("cfg", MODE_PRIVATE)
        val internal = cfg.getBoolean("audio_internal", true)
        val micEnabled = cfg.getBoolean("audio_mic", true) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        try {
            recW = W; recH = H
            rec = Recorder(this, proj, W, H, internal, micEnabled)
            bubble.rec = true; bubble.invalidate()
            val audioLabel = when {
                internal && micEnabled -> "صدای داخل و بیرون گوشی"
                internal -> "صدای داخل گوشی"
                micEnabled -> "صدای بیرون گوشی"
                else -> "بدون صدا"
            }
            Toast.makeText(this, "ضبط شروع شد ($audioLabel) • برای ترجمه حباب را لمس کن", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ضبط خودکار شروع نشد: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleRec() {
        val current = rec
        if (current == null) {
            startRecordingAutomatically()
        } else {
            rec = null
            Thread { current.stop() }.start()
            Toast.makeText(this, "ضبط ذخیره شد: Movies/BubbleTranslate", Toast.LENGTH_LONG).show()
        }
        bubble.rec = rec != null
        bubble.invalidate()
    }

    // لمس حباب = روشن/خاموش.
    //  روشن: فقط همین صفحه‌ی لحظه‌ی لمس، یک بار ترجمه می‌شود و تا لمس بعدی همان‌طور می‌ماند.
    //  خاموش: ترجمه پاک می‌شود. هیچ اسکن یا ترجمه‌ی خودکاری وجود ندارد.
    private fun toggle() {
        live = !live
        val id = ++scanId
        scanJob?.cancel()
        scanJob = null
        shown = emptyList()
        overlay.show(emptyList())
        bubble.on = live
        bubble.busy = live          // نارنجی تا ترجمه تمام شود
        bubble.invalidate()
        if (live) scanJob = scope.launch { translateOnce(id) }
    }

    // ترجمه‌ی دوباره‌ی صفحه‌ی فعلی (فقط وقتی خودت از منوی حباب بزنی؛ مثلاً بعد از اسکرول)
    private fun rescan() {
        if (!live) { toggle(); return }
        val id = ++scanId
        scanJob?.cancel()
        shown = emptyList()
        bubble.busy = true
        bubble.invalidate()
        scanJob = scope.launch { translateOnce(id) }
    }

    private suspend fun toast(msg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(this@TranslateService, msg, Toast.LENGTH_SHORT).show()
    }

    // یک بار: تصویر تمیز صفحه → OCR → ترجمه‌ی تک‌تک متن‌ها → کشیدن دقیقاً روی متن اصلی
    private suspend fun translateOnce(id: Int) {
        val sw = W
        val sh = H
        val d = resources.displayMetrics.density
        var left = 0
        var top = 0
        var rw = sw
        var rh = sh
        if (mode == 12) {                       // زیرنویس: فقط بخش پایین صفحه
            top = (sh * 0.58f).toInt()
            rh = (sh - top).coerceAtLeast(1)
        } else if (!whole) {                    // فقط اطراف حباب
            val bp = bubbleParams ?: return
            val cx = bp.x + bubbleSize / 2
            val cy = bp.y + bubbleSize / 2
            rw = minOf(sw, (560 * d).toInt()).coerceAtLeast(1)
            rh = minOf(sh, (260 * d).toInt()).coerceAtLeast(1)
            left = (cx - rw / 2).coerceIn(0, (sw - rw).coerceAtLeast(0))
            top = (cy - rh / 2).coerceIn(0, (sh - rh).coerceAtLeast(0))
        }
        modelErr = false

        try {
            // حباب و ترجمه‌ی قبلی موقتاً پنهان می‌شوند تا OCR تصویر تمیز بگیرد.
            withContext(Dispatchers.Main) {
                overlay.show(emptyList())
                bubble.visibility = View.INVISIBLE
            }
            delay(200)      // تا تغییر پنهان‌شدن حباب داخل فریم بعدی بیاید
            var frame: Bitmap? = null
            var waited = 0
            while (live && frame == null && waited < 1800) {
                frame = snapshot(true)
                if (frame == null) { delay(40); waited += 40 }
            }
            if (!live) return
            val full = frame
            if (full == null) { toast("تصویر صفحه گرفته نشد؛ دوباره بزن"); return }
            if (full.width != sw || full.height != sh) { toast("صفحه چرخید؛ دوباره بزن"); return }
            withContext(Dispatchers.Main) { bubble.visibility = View.VISIBLE }   // تصویر در دست است؛ حباب برمی‌گردد

            val crop = Bitmap.createBitmap(full, left, top, rw, rh)
            val result = try {
                recognizer.process(InputImage.fromBitmap(crop, 0)).await()
            } finally {
                if (crop !== full) crop.recycle()
            }
            if (!live) return
            val segs = buildSegs(result, left, top)
            if (segs.isEmpty()) { toast("متنی در این محدوده پیدا نشد"); return }
            ensureModels()

            val items = ArrayList<Item>()
            for (s in segs) {
                if (!live) return
                if (!worth(s.text)) continue
                val tr = convert(s.text) ?: continue
                if (tr == s.text) continue
                val box = RectF(s.box).apply { inset(-5f, -3f) }
                val bg = sampleBg(full, box)
                val fg = if (Color.luminance(bg) > 0.5f) Color.BLACK else Color.WHITE
                items.add(Item(box, Color.argb(235, Color.red(bg), Color.green(bg), Color.blue(bg)), fit(tr, box, fg, s.lines)))
                if (items.size % 6 == 0) {      // نمایش تدریجی تا منتظر آخر نمانی
                    val part = items.toList()
                    shown = part
                    withContext(Dispatchers.Main) { overlay.show(part) }
                }
            }
            if (!live) return
            val fin = items.toList()
            shown = fin
            withContext(Dispatchers.Main) { overlay.show(fin) }
            if (fin.isEmpty()) toast(if (modelErr) "مدل ترجمه روی گوشی نیست؛ در برنامه دکمه ۱ را (با اینترنت) بزن" else "متنی برای ترجمه پیدا نشد")
            else if (modelErr) toast("بعضی متن‌ها ترجمه نشدند؛ در برنامه دکمه ۱ را (با اینترنت) بزن")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (live) toast("خواندن یا ترجمه‌ی متن ناموفق بود؛ دوباره بزن")
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                if (::bubble.isInitialized) {
                    bubble.visibility = View.VISIBLE
                    if (id == scanId) {          // هنوز همین ترجمه فعال است (کاربر خاموش/دوباره‌ترجمه نکرده)
                        bubble.busy = false
                        if (live && shown.isEmpty()) { live = false; bubble.on = false }   // چیزی ترجمه نشد → حباب خاموش برمی‌گردد
                        bubble.invalidate()
                    }
                }
                if (!live) overlay.show(emptyList())
            }
        }
    }

    // خروجی OCR → تکه‌های قابل ترجمه بر اساس حالت (خط‌به‌خط / بلوک / زیرنویس)
    private fun buildSegs(res: com.google.mlkit.vision.text.Text, dx: Int, dy: Int): List<Seg> {
        fun mv(r: Rect) = RectF(r).apply { offset(dx.toFloat(), dy.toFloat()) }
        val lines = res.textBlocks.flatMap { it.lines }
        return when (mode) {
            11 -> res.textBlocks.mapNotNull { b ->
                b.boundingBox?.let { Seg(mv(it), b.lines.joinToString(" ") { l -> l.text }, b.lines.size.coerceAtLeast(1)) }
            }
            12 -> {
                val ok = lines.filter { it.boundingBox != null }.sortedBy { it.boundingBox!!.top }
                if (ok.isEmpty()) emptyList<Seg>() else {
                    val u = mv(ok[0].boundingBox!!)
                    for (l in ok) u.union(mv(l.boundingBox!!))
                    listOf(Seg(u, ok.joinToString(" ") { it.text }, ok.size))
                }
            }
            else -> lines.mapNotNull { l -> l.boundingBox?.let { Seg(mv(it), l.text, 1) } }
        }
    }

    // فقط متن‌هایی که حرف دارند (نه آیکون و نویز)
    private fun worth(t: String): Boolean {
        var letters = 0
        for (ch in t) if (ch.isLetter()) { letters++; if (ch.code > 0x0900) return true }
        return letters >= 2
    }

    // مدل‌های ترجمه یک بار آماده می‌شوند (اگر قبلاً دانلود شده باشند فوری است و اینترنت لازم نیست)
    private suspend fun ensureModels(): Boolean {
        if (modelsOk) return true
        val c = DownloadConditions.Builder().build()
        val ok = try {
            withTimeoutOrNull(25_000L) {
                translator?.downloadModelIfNeeded(c)?.await()
                trFa?.downloadModelIfNeeded(c)?.await()
                true
            } ?: false
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { false }
        modelsOk = ok
        return ok
    }

    // ترجمه‌ی یک متن:
    //  • فینگلیش → فارسی با قاعده (و اگر مقصد فارسی نیست، فارسی → مقصد)
    //  • انگلیسی/زبان خارجی → مدل ML Kit؛ بعدش هر اسم یا کلمه‌ی لاتین باقی‌مانده با حروف فارسی نوشته می‌شود
    private suspend fun convert(text: String): String? {
        cache[text]?.let { return it }
        val fgl = srcRaw == Langs.FGL || (srcRaw == Langs.AUTO && Finglish.isFinglish(text))
        val out: String = try {
            if (fgl) {
                val fa = Finglish.toPersian(text, all)
                if (dstFa) fa else (trFa?.translate(fa)?.await() ?: fa)
            } else {
                val t = translator
                if (t != null && !modelsOk) { modelErr = true; return null }
                val r = if (t != null) t.translate(text).await() else text
                if (dstFa && all) Finglish.toPersian(r, true, true) else r
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { modelErr = true; return null }
        if (cache.size > 4000) cache.clear()
        cache[text] = out
        return out
    }

    private fun px(b: Bitmap, x: Float, y: Float) =
        b.getPixel(x.toInt().coerceIn(0, b.width - 1), y.toInt().coerceIn(0, b.height - 1))

    // رنگ پس‌زمینه = میانه نمونه‌های اطراف کادر متن (در برابر نویز صحنه مقاوم است)
    private fun sampleBg(b: Bitmap, r: RectF): Int {
        val pts = ArrayList<Int>()
        for (x in listOf(r.left - 4, r.right + 4)) for (y in listOf(r.top + 2, r.centerY(), r.bottom - 2)) pts.add(px(b, x, y))
        for (x in listOf(r.left + 2, r.centerX(), r.right - 2)) for (y in listOf(r.top - 4, r.bottom + 4)) pts.add(px(b, x, y))
        fun med(f: (Int) -> Int) = pts.map(f).sorted()[pts.size / 2]
        return Color.rgb(med { Color.red(it) }, med { Color.green(it) }, med { Color.blue(it) })
    }

    // بزرگ‌ترین فونتی که متن ترجمه را جا بدهد؛ n = تعداد خط‌های متن اصلی
    // (یک‌خطی: اگر لازم باشد کادر به راست باز می‌شود؛ چندخطی: متن داخل کادر می‌شکند)
    private fun fit(text: String, box: RectF, fg: Int, n: Int): StaticLayout {
        val one = n <= 1
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = fg }
        val maxW = (W - box.left - 4f).coerceAtLeast(box.width())
        var size = (box.height() / n.coerceAtLeast(1)).coerceAtMost(64f) * 0.8f
        lateinit var l: StaticLayout
        do {
            p.textSize = size
            val w = if (one) minOf(maxOf(Layout.getDesiredWidth(text, p), box.width()), maxW).toInt() + 1
            else box.width().toInt().coerceAtLeast(40)
            l = StaticLayout.Builder.obtain(text, 0, text.length, p, w)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).build()
            size -= 1f
            val ok = if (one) l.lineCount == 1 else l.height <= box.height()
        } while (!ok && size > 9f)
        box.right = maxOf(box.right, box.left + l.width)
        return l
    }

    override fun onDestroy() {
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        if (::wm.isInitialized) {
            hideMenu()
            runCatching { wm.removeView(overlay) }
            runCatching { wm.removeView(bubble) }
        }
        rec?.let { r -> rec = null; Thread { r.stop() }.start() }
        synchronized(lock) { pending?.close(); pending = null }
        vd?.release()
        if (::reader.isInitialized) reader.close()
        frameThread.quitSafely()
        if (::proj.isInitialized) proj.stop()
        scanJob?.cancel()
        translator?.close()
        trFa?.close()
        if (::recognizer.isInitialized) recognizer.close()
        super.onDestroy()
    }
}