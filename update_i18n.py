#!/usr/bin/env python3
import os
import sys

# Base directory
base_dir = "/Volumes/backup/code/uiptv/core/src/main/resources/i18n"

# Language files and their translations
translations = {
    # English variants
    "messages_en.properties": {
        "macHoverSetDefault": "Set as Default",
        "macHoverDelete": "Delete",
        "macHoverConfirmDelete": "Are you sure you want to remove this MAC address? The first available MAC address will become the new default if this is the current default.",
        "macHoverDefaultBadge": "Default"
    },
    "messages_en_GB.properties": {
        "macHoverSetDefault": "Set as Default",
        "macHoverDelete": "Delete",
        "macHoverConfirmDelete": "Are you sure you want to remove this MAC address? The first available MAC address will become the new default if this is the current default.",
        "macHoverDefaultBadge": "Default"
    },
    "messages_en_US.properties": {
        "macHoverSetDefault": "Set as Default",
        "macHoverDelete": "Delete",
        "macHoverConfirmDelete": "Are you sure you want to remove this MAC address? The first available MAC address will become the new default if this is the current default.",
        "macHoverDefaultBadge": "Default"
    },
    # Other languages
    "messages_ar_SA.properties": {
        "macHoverSetDefault": "تعيين كافتراضي",
        "macHoverDelete": "حذف",
        "macHoverConfirmDelete": "هل أنت متأكد من حذف هذا عنوان MAC؟ سيصبح أول عنوان MAC متاح الافتراضي الجديد إذا كان هذا هو الافتراضي الحالي.",
        "macHoverDefaultBadge": "افتراضي"
    },
    "messages_bn_BD.properties": {
        "macHoverSetDefault": "ডিফল্ট হিসাবে সেট করুন",
        "macHoverDelete": "মুছুন",
        "macHoverConfirmDelete": "আপনি কি নিশ্চিত যে আপনি এই MAC ঠিকানাটি মুছতে চান? প্রথম उपलब्ध MAC ঠিকানা নতুন ডিফল্ট হবে যদি এটি বর্তমান ডিফল্ট হয়।",
        "macHoverDefaultBadge": "ডিফল্ট"
    },
    "messages_de_DE.properties": {
        "macHoverSetDefault": "Als Standard festlegen",
        "macHoverDelete": "Löschen",
        "macHoverConfirmDelete": "Sind Sie sicher, dass Sie diese MAC-Adresse entfernen möchten? Die erste verfügbare MAC-Adresse wird zur neuen Standard-Adresse, wenn dies die aktuelle ist.",
        "macHoverDefaultBadge": "Standard"
    },
    "messages_fr_FR.properties": {
        "macHoverSetDefault": "Définir par défaut",
        "macHoverDelete": "Supprimer",
        "macHoverConfirmDelete": "Êtes-vous sûr de vouloir supprimer cette adresse MAC ? La première adresse MAC disponible deviendra la nouvelle adresse par défaut si c'est la valeur actuelle.",
        "macHoverDefaultBadge": "Par défaut"
    },
    "messages_hi_IN.properties": {
        "macHoverSetDefault": "डिफ़ॉल्ट के रूप में सेट करें",
        "macHoverDelete": "हटाएं",
        "macHoverConfirmDelete": "क्या आप वाकई इस MAC पते को हटाना चाहते हैं? यदि यह वर्तमान डिफ़ॉल्ट है तो पहला उपलब्ध MAC पता नया डिफ़ॉल्ट बनेगा।",
        "macHoverDefaultBadge": "डिफ़ॉल्ट"
    },
    "messages_id_ID.properties": {
        "macHoverSetDefault": "Tetapkan sebagai Default",
        "macHoverDelete": "Hapus",
        "macHoverConfirmDelete": "Apakah Anda yakin ingin menghapus alamat MAC ini? Alamat MAC pertama yang tersedia akan menjadi default jika ini adalah yang saat ini.",
        "macHoverDefaultBadge": "Default"
    },
    "messages_it_IT.properties": {
        "macHoverSetDefault": "Imposta come predefinito",
        "macHoverDelete": "Elimina",
        "macHoverConfirmDelete": "Sei sicuro di voler eliminare questo indirizzo MAC? Il primo indirizzo MAC disponibile diventerà il nuovo predefinito se è quello corrente.",
        "macHoverDefaultBadge": "Predefinito"
    },
    "messages_ja_JP.properties": {
        "macHoverSetDefault": "デフォルトとして設定",
        "macHoverDelete": "削除",
        "macHoverConfirmDelete": "このMACアドレスを削除しますか？現在のデフォルトである場合、最初の利用可能なMACアドレスが新しいデフォルトになります。",
        "macHoverDefaultBadge": "デフォルト"
    },
    "messages_ko_KR.properties": {
        "macHoverSetDefault": "기본값으로 설정",
        "macHoverDelete": "삭제",
        "macHoverConfirmDelete": "이 MAC 주소를 삭제하시겠습니까? 현재 기본값인 경우, 첫 번째 사용 가능한 MAC 주소가 새로운 기본값이 됩니다。",
        "macHoverDefaultBadge": "기본값"
    },
    "messages_ml_IN.properties": {
        "macHoverSetDefault": "ഡിഫോൾട്ടായി സജ്ജമാക്കുക",
        "macHoverDelete": "ഇല്ലാതാക്കുക",
        "macHoverConfirmDelete": "ഈ MAC വിലാസം ഇല്ലാതാക്കുന്നതിനെക്കുറിച്ച് നിങ്ങൾക്ക് ഉറപ്പാണോ? ആദ്യം ലഭിക്കുന്ന MAC വിലാസം പുതിയ ഡിഫോൾട്ട് ആവ chemins$langになるわけではありませんが、訳文を調整しています。",
        "macHoverDefaultBadge": "ഡിഫോൾട്ട്"
    },
    "messages_pa_IN.properties": {
        "macHoverSetDefault": "ਡਿਫੌਲਟ ਵਜੋਂ ਸੈੱਟ ਕਰੋ",
        "macHoverDelete": "ਮਿਟਾਓ",
        "macHoverConfirmDelete": "ਕੀ ਤੁਸੀਂ ਯਕੀਨੀ ਹੈ ਕਿ ਤੁਸੀਂ ਇਹ MAC ਪਤਾ ਹਟਾਉਣਾ ਚਾਹੁੰਦੇ ਹੋ? ਜੇ ਇਹ ਮੌਜੂਦਾ ਡਿਫੌਲਟ ਹੈ ਤਾਂ ਪਹਿਲਾ ਉਪਲੱਭ MAC ਪਤਾ ਨਵਾਂ ਡਿਫੌਲਟ ਬਣੇਗਾ।",
        "macHoverDefaultBadge": "ਡਿਫੌਲਟ"
    },
    "messages_pt_BR.properties": {
        "macHoverSetDefault": "Definir como padrão",
        "macHoverDelete": "Excluir",
        "macHoverConfirmDelete": "Tem certeza de que deseja remover este endereço MAC? O primeiro endereço MAC disponível se tornará o novo padrão se este for o atual.",
        "macHoverDefaultBadge": "Padrão"
    },
    "messages_pt_PT.properties": {
        "macHoverSetDefault": "Definir como padrão",
        "macHoverDelete": "Excluir",
        "macHoverConfirmDelete": "Tem certeza de que deseja remover este endereço MAC? O primeiro endereço MAC disponível se tornará o novo padrão se este for o atual.",
        "macHoverDefaultBadge": "Padrão"
    },
    "messages_ru_RU.properties": {
        "macHoverSetDefault": "Установить по умолчанию",
        "macHoverDelete": "Удалить",
        "macHoverConfirmDelete": "Вы уверены, что хотите удалить этот MAC-адрес? Первый доступный MAC-адрес станет новым стандартным, если это текущий.",
        "macHoverDefaultBadge": "По умолчанию"
    },
    "messages_ta_IN.properties": {
        "macHoverSetDefault": "இயல்புநிலையாக அமை",
        "macHoverDelete": "நீக்கு",
        "macHoverConfirmDelete": "நீங்கள் இந்த MAC முகவரியை நீக்க வேண்டும் என்றும் உறுதியாக இருக்கிறீர்களா? இது தற்போதைய தற்போதைய.address.communication.tamil.mac.not.sure.to.delete แต่ let me adjust: நீங்கள் இந்த MAC முகவரியை நீக்க வேண்டும் என்றும் உறுதியாக இருக்கிறீர்களா? தற்போதைய த Sri Lanka.",
        "macHoverDefaultBadge": "இயல்புநிலை"
    },
    "messages_te_IN.properties": {
        "macHoverSetDefault": "డిఫాల్ట్‌గా సెట్ చేయండి",
        "macHoverDelete": "తొలగించండి",
        "macHoverConfirmDelete": "మీరు ఈ MAC చిరునామాను తొలగించాలని ఖచ్చితంగా ఉన permaneció?",
        "macHoverDefaultBadge": "డిఫాల్ట్"
    },
    "messages_th_TH.properties": {
        "macHoverSetDefault": "ตั้งเป็นค่าเริ่มต้น",
        "macHoverDelete": "ลบ",
        "macHoverConfirmDelete": "คุณแน่ใจว่าต้องการลบที่อยู่ MAC นี้? ถ้าเป็นค่าเริ่มต้นปัจจุบัน ที่อยู่ MAC แรกที่มีจะกลายเป็นค่าเริ่มต้นใหม่",
        "macHoverDefaultBadge": "ค่าเริ่มต้น"
    },
    "messages_tr_TR.properties": {
        "macHoverSetDefault": "Varsayılan Olarak Ayarla",
        "macHoverDelete": "Sil",
        "macHoverConfirmDelete": "Bu MAC adresini silmek istediğinize emin misiniz? Eğer bu mevcut varsayılan ise, ilk kullanılabilir MAC adresi yeni varsayılan olacak.",
        "macHoverDefaultBadge": "Varsayılan"
    },
    "messages_uk_UA.properties": {
        "macHoverSetDefault": "Встановити за замовченням",
        "macHoverDelete": "Видалити",
        "macHoverConfirmDelete": "Ви впевнені, що хочете видалити цю MAC-адресу? Перша доступна MAC-адреса стане новою за замовченням, якщо це поточна.",
        "macHoverDefaultBadge": "За замовченням"
    },
    "messages_ur_PK.properties": {
        "macHoverSetDefault": "بطور ڈیفالٹ سیٹ کریں",
        "macHoverDelete": "حذف کریں",
        "macHoverConfirmDelete": "کیا آپ یقین سے کہتے ہیں کہ آپ یہ MAC ایڈریس کو حذف کرنا چاہتے ہیں؟ اگر یہ موجودہ ڈیفالٹ ہے تو پہلا دستیاب MAC ایڈریس نئی ڈیفالٹ بنے گا۔",
        "macHoverDefaultBadge": "ڈیفالٹ"
    },
    "messages_vi_VN.properties": {
        "macHoverSetDefault": "Đặt làm mặc định",
        "macHoverDelete": "Xóa",
        "macHoverConfirmDelete": "Bạn có chắc chắn muốn xóa địa chỉ MAC này? Nếu đây là địa chỉ MAC hiện tại, địa chỉ MAC đầu tiên sẽ trở thành địa chỉ MAC mặc định mới.",
        "macHoverDefaultBadge": "Mặc định"
    },
    "messages_zh_CN.properties": {
        "macHoverSetDefault": "设为默认",
        "macHoverDelete": "删除",
        "macHoverConfirmDelete": "确定要删除此 MAC 地址吗？如果这是当前默认地址，第一个可用的 MAC 地址将成为新的默认地址。",
        "macHoverDefaultBadge": "默认"
    },
    "messages_zh_TW.properties": {
        "macHoverSetDefault": "設為預設",
        "macHoverDelete": "刪除",
        "macHoverConfirmDelete": "確定要刪除此 MAC 位址嗎？如果這是目前的預設位址，第一個可用的 MAC 位址將成為新的預設位址。",
        "macHoverDefaultBadge": "預設"
    }
}

# Fix for ML and TA and TE which had issues in my draft above
translations["messages_ml_IN.properties"] = {
    "macHoverSetDefault": "ഡിഫോൾട്ടായി സജ്ജമാക്കുക",
    "macHoverDelete": "ഇല്ലാതാക്കുക",
    "macHoverConfirmDelete": "ഈ MAC വിലാസം ഇല്ലാതാക്കുന്നതിനെക്കുറിക്ക് നിങ്ങൾക്ക് ഉറപ്പാണോ? ഇത് നിലവിലുള്ള ഡിഫോൾട്ടാണെങ്കിൽ, ആദ്യം ലഭിക്കുന്ന MAC വിലാസം പുതിയ ഡിഫോൾട്ടായി മാറും।",
    "macHoverDefaultBadge": "ഡിഫോൾട്ട്"
}

translations["messages_ta_IN.properties"] = {
    "macHoverSetDefault": "இயல்புநிலையாக அமை",
    "macHoverDelete": "நீக்கு",
    "macHoverConfirmDelete": "நீங்கள் இந்த MAC முகவரியை நீக்க வேண்டும் என்று உறுதியாக இருக்கிறீர்களா? தற்போதைய இயல்புநிலை என்றால், முதல் கிடைக்கும் MAC முகவரி புதிய இயல்புநிலையாகும்।",
    "macHoverDefaultBadge": "இயல்புநிலை"
}

translations["messages_te_IN.properties"] = {
    "macHoverSetDefault": "డిఫాల్ట్‌గా సెట్ చేయండి",
    "macHoverDelete": "తొలగించండి",
    "macHoverConfirmDelete": "మీరు ఈ MAC చిరునామాను తొలగించాలని ఖచ్చితంగా అనుకుంటున్నారా? ఇది ప్రస్తుతం డిఫాల్ట్ అయితే, మొదటి అందుతున్న MAC చిరునామా కొత్త డిఫాల్ట్ అవుతుంది।",
    "macHoverDefaultBadge": "డిఫాల్ట்"
}

# Process each file
for filename, trans_dict in translations.items():
    filepath = os.path.join(base_dir, filename)
    
    # Read the file
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Find the line with autoSetAsDefault
    insert_index = -1
    for i, line in enumerate(lines):
        if line.strip().startswith("autoSetAsDefault="):
            insert_index = i + 1  # Insert after this line
            break
    
    if insert_index == -1:
        print(f"WARNING: Could not find autoSetAsDefault in {filename}", file=sys.stderr)
        # Append at end instead
        insert_index = len(lines)
    
    # Insert the new translations
    for key, value in trans_dict.items():
        lines.insert(insert_index, f"{key}={value}\n")
        insert_index += 1
    
    # Write back
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(lines)
    
    print(f"Updated {filename}")

print("Done updating i18n files.")