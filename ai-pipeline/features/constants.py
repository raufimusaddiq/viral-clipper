"""Static scoring constants: default feature weights, Indonesian word lists,
boost / penalty schedules, hashtag mapping, CTA phrases.

Pulled out so they can be tuned without touching orchestration code.
"""

DEFAULT_WEIGHTS = {
    # Rebalanced in P3.5-B: every prior weight shaved ~8% to make room for
    # the two new features. Ratios between existing features are preserved.
    # Sum = 1.00.
    "hookStrength": 0.17,
    "keywordTrigger": 0.08,
    "novelty": 0.08,
    "clarity": 0.09,
    "emotionalEnergy": 0.08,
    "textSentiment": 0.05,
    "pauseStructure": 0.06,
    "facePresence": 0.09,
    "sceneChange": 0.07,
    "topicFit": 0.07,
    "historyScore": 0.06,
    # New in P3.5-B:
    "motion": 0.05,
    "onsetDensity": 0.05,
}

HOOK_PHRASES = [
    "rahasia", "penting", "perhatikan", "simak", "tahukah kamu",
    "tidak banyak orang tahu", "jangan", "wajib", "harus",
    "kamu tahu tidak", "coba bayangkan", "faktanya", "sesuatu yang",
    "gila", "luar biasa", "aneh", "mengejutkan",
]

KEYWORD_TRIGGERS = [
    "rahasia", "penting", "tidak banyak orang tahu", "kesalahan", "ternyata",
    "wajib", "harus", "jangan", "bahaya", "untung", "sayangnya", "fakta",
    "curhat", "jebakan", "trik", "hack", "tip", "solusi",
    "bohong", "benar", "buktinya", "nyata", "gue", "lu", "lo",
    "banget", "parah", "gila", "serius", "beneran",
    "unik", "aneh", "langka", "jarang", "mustahil",
    "mengubah", "menginspirasi", "membuktikan", "membongkar",
]

EMOTION_WORDS = [
    "sedih", "marah", "kaget", "wow", "amazing", "senang", "kecewa",
    "bangga", "takut", "haru", "benci", "cinta", "panic",
    "shock", "greget", "geram", "syukur", "bersyukur", "penasaran",
]

POSITIVE_WORDS = [
    "bagus", "baik", "hebat", "indah", "cantik", "keren", "sukses",
    "berhasil", "bahagia", "senang", "luar biasa", "mantap", "jos",
    "top", "istimewa", "menakjubkan", "fantastis", "sempurna", "puas",
    "memuaskan", "bermanfaat", "berguna", "efektif", "mudah", "praktis",
    "hemat", "murah", "gratis", "bonus", "untung", "beruntung",
    "tepat", "akurat", "jelas", "nyata", "aman", "nyaman", "sehat",
    "kuat", "cepat", "solusi", "trik", "tip", "hack",
    "inspirasi", "motivasi", "berharga", "positif", "optimis",
    "menguntungkan", "pintar", "cerdas", "kreatif", "inovatif",
]

NEGATIVE_WORDS = [
    "jelek", "buruk", "gagal", "rusak", "hilang", "mati", "error",
    "bug", "lambat", "mahal", "boros", "ribet", "sulit", "susah",
    "payah", "lemah", "bodoh", "tolol", "sampah", "buang waktu",
    "penipuan", "tipu", "bohong", "dusta", "palsu", "kecewa",
    "sedih", "marah", "geram", "parah", "mengerikan", "bahaya",
    "bingung", "pusing", "sakit", "risiko", "ancaman", "masalah",
    "problem", "rugi", "merugikan", "negatif", "pesimis", "takut",
    "cemas", "khawatir", "stres", "depresi", "frustrasi",
]

CONVERSATION_MARKERS = [
    "kan", "ya", "dong", "sih", "kok", "nih", "tuh", "deh",
    "loh", "nah", "duh", "aduh", "wah", "ih", "eh",
]

CONFLICT_WORDS = [
    "tapi", "namun", "sebenarnya", "beda sama", "salah", "benar",
    "memang", "boleh dibilang", "sebaliknya", "padahal", "ternyata",
    "malah", "justru", "nyatanya",
]

QUESTION_WORDS = [
    "apa", "kenapa", "kok", "bagaimana", "kapan", "siapa", "dimana",
    "berapa", "mengapa", "gimana",
]

BOOST_CONDITIONS = {
    "sharp_question": 0.05,
    "opinion_conflict": 0.05,
    "number_list": 0.03,
    "emotional_moment": 0.05,
    "conversational_tone": 0.03,
}

PENALTY_CONDITIONS = {
    "slow_opening": 0.08,
    "too_much_silence": 0.07,
    "too_generic": 0.05,
    "no_face": 0.04,
}

HASHTAG_MAP = {
    "rahasia": ["#rahasia", "#faktamenarik"],
    "penting": ["#penting", "#wajibtau"],
    "trik": ["#trik", "#tips"],
    "hack": ["#hack", "#lifehack"],
    "tip": ["#tips", "#trik"],
    "bahaya": ["#bahaya", "#peringatan"],
    "ternyata": ["#ternyata", "#fakta"],
    "fakta": ["#fakta", "#faktamenarik"],
    "kaget": ["#kaget", "#wow"],
    "gila": ["#gila", "#wow"],
    "parah": ["#parah", "#viral"],
    "serius": ["#serius", "#nyata"],
    "aneh": ["#aneh", "#unik"],
    "langka": ["#langka", "#unik"],
    "solusi": ["#solusi", "#tips"],
    "jebakan": ["#jebakan", "#hatihati"],
    "sedih": ["#sedih", "#motivasi"],
    "marah": ["#marah", "#geram"],
    "cinta": ["#cinta", "#romantis"],
    "motivasi": ["#motivasi", "#inspirasi"],
    "bohong": ["#bohong", "#fakta"],
    "menginspirasi": ["#inspirasi", "#motivasi"],
}

GENERIC_HASHTAGS = ["#fyp", "#foryou", "#viral", "#tiktokindonesia"]

CTA_PHRASES = [
    "Simak sampai habis!",
    "Follow untuk konten lainnya!",
    "Share ke temen kamu!",
    "Save buat nanti!",
]
