package az.cachecleaner

import java.util.Locale

object Matchers {

    private val cacheWords = listOf(
        "cache", "cach", "keş", "önbellek", "önbelleğ", "onbellek", "onbelleg",
        "кэш", "кеш", "缓存", "緩存", "快取", "キャッシュ", "캐시",
        "ذاكرة التخزين المؤقت", "التخزين المؤقت", "कैश", "bộ nhớ đệm", "bộ nhớ cache",
        "pamięć podręczn", "pamięci podręczn", "välimuist", "zwischenspeicher",
        "mezipam", "gyorsítótár", "מטמון", "แคช"
    )

    private val verbWords = listOf(
        "clear", "delete", "remove", "wipe", "təmizlə", "sil", "temizle", "очист", "удал",
        "borrar", "vaciar", "eliminar", "vider", "supprimer", "effacer", "leeren", "löschen",
        "svuota", "cancella", "elimina", "limpar", "apagar", "清除", "清理", "删除", "刪除",
        "削除", "消去", "クリア", "삭제", "지우", "비우", "مسح", "حذف", "إزالة", "साफ", "हटा",
        "xóa", "hapus", "bersih", "kosong", "wyczy", "usuń", "wis", "verwijder", "töm", "rensa",
        "ryd", "tyhjen", "poista", "נקה", "ล้าง", "törl", "vymaz", "șterge", "sterge"
    )

    // "Məlumat/data" sözləri: bunlar varsa düymə heç vaxt kliklənmir
    private val dataWords = listOf(
        "data", "veri", "məlumat", "данн", "datos", "données", "donnees", "daten", "dati",
        "dados", "数据", "數據", "資料", "データ", "데이터", "بيانات", "डेटा", "dữ liệu",
        "dane", "gegevens", "uppgifter", "tiedot", "adat"
    )

    private val storageWords = listOf(
        "storage", "yaddaş", "depolama", "хранилищ", "память", "almacenamiento", "stockage",
        "speicher", "archiviazione", "armazenamento", "存储", "存儲", "儲存", "ストレージ",
        "저장", "التخزين", "स्टोरेज", "bộ nhớ", "lưu trữ", "pamięć", "opslag", "lagring",
        "tallennus", "penyimpanan", "tárhely", "memória"
    )

    private val sizeRe = Regex(
        """(\d+(?:[.,]\d+)?)\s*(bytes?|байт|kib|mib|gib|kb|mb|gb|кб|мб|гб|b)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    fun norm(s: String): String =
        s.lowercase(Locale.ROOT).replace("\u0307", "").trim()

    private fun has(n: String, list: List<String>) = list.any { n.contains(it) }

    /** "Clear cache" düyməsi: keş sözü + təmizləmə felidir, "data" sözü YOXDUR. */
    fun isCacheButton(raw: String?): Boolean {
        val n = norm(raw ?: return false)
        return n.length <= 40 && has(n, cacheWords) && has(n, verbWords) && !has(n, dataWords)
    }

    /** "Cache" başlığı (ölçü oxumaq üçün). */
    fun isCacheTitle(raw: String?): Boolean {
        val n = norm(raw ?: return false)
        return n.length <= 30 && has(n, cacheWords) && !has(n, verbWords) && !has(n, dataWords)
    }

    /** App info içindəki "Storage" sətri (mətn Storage sözü ilə başlamalıdır). */
    fun isStorageRow(raw: String?): Boolean {
        val n = norm(raw ?: return false)
        return n.length <= 40 && storageWords.any { n.startsWith(it) } &&
                !has(n, verbWords) && !has(n, dataWords)
    }

    /** Yalnız Xiaomi/HyperOS: "Clear data" düyməsi menyu açır; heç nə təsdiqlənmir. */
    fun isMiuiMenuEntry(raw: String?): Boolean {
        val n = norm(raw ?: return false)
        return n.length <= 30 && has(n, dataWords) && has(n, verbWords) && !has(n, cacheWords)
    }

    fun parseSize(raw: String?): Long? {
        val m = sizeRe.find(raw ?: return null) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val u = m.groupValues[2].lowercase(Locale.ROOT)
        val k = 1024.0
        val mult = when {
            u.startsWith("k") || u.startsWith("к") -> k
            u.startsWith("m") || u.startsWith("м") -> k * k
            u.startsWith("g") || u.startsWith("г") -> k * k * k
            else -> 1.0
        }
        return (v * mult).toLong()
    }
}
