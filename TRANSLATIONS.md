# Translation Volunteers

UIPTV needs open source volunteers to help improve and maintain human translations across all supported locales.

Machine translation is not enough for this project. The UI includes account management, playback controls, settings, metadata, tabs, filters, and locale-specific labels that need clear and natural wording from native speakers. If UIPTV supports your language, or should support your language better, your help is needed.

## How You Can Help

Please contribute translations in your native language and review existing wording for:

- accuracy
- clarity
- natural phrasing
- consistency
- punctuation and capitalization
- placeholders and formatting tokens

When editing translation files:

1. Use the English/base messages as the reference.
2. Translate only the text to the right of `=`.
3. Do not change property keys.
4. Preserve placeholders such as `{0}`, `{1}`, `%s`, or any similar variables exactly as they are.
5. Preserve escaping, special characters, and line structure.
6. Keep terminology consistent across the full locale.
7. If a string should remain in English for technical reasons, leave it unchanged rather than guessing.

## Files That Need Human Translation

These are the source translation property files that need human review and improvement:

- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_ar_SA.properties`
- `src/main/resources/i18n/messages_bn_BD.properties`
- `src/main/resources/i18n/messages_de_DE.properties`
- `src/main/resources/i18n/messages_en.properties`
- `src/main/resources/i18n/messages_en_GB.properties`
- `src/main/resources/i18n/messages_en_US.properties`
- `src/main/resources/i18n/messages_es_ES.properties`
- `src/main/resources/i18n/messages_fr_FR.properties`
- `src/main/resources/i18n/messages_hi_IN.properties`
- `src/main/resources/i18n/messages_id_ID.properties`
- `src/main/resources/i18n/messages_it_IT.properties`
- `src/main/resources/i18n/messages_ja_JP.properties`
- `src/main/resources/i18n/messages_ko_KR.properties`
- `src/main/resources/i18n/messages_ml_IN.properties`
- `src/main/resources/i18n/messages_pa_IN.properties`
- `src/main/resources/i18n/messages_pt_BR.properties`
- `src/main/resources/i18n/messages_pt_PT.properties`
- `src/main/resources/i18n/messages_ru_RU.properties`
- `src/main/resources/i18n/messages_ta_IN.properties`
- `src/main/resources/i18n/messages_te_IN.properties`
- `src/main/resources/i18n/messages_th_TH.properties`
- `src/main/resources/i18n/messages_tr_TR.properties`
- `src/main/resources/i18n/messages_uk_UA.properties`
- `src/main/resources/i18n/messages_ur_PK.properties`
- `src/main/resources/i18n/messages_vi_VN.properties`
- `src/main/resources/i18n/messages_zh_CN.properties`
- `src/main/resources/i18n/messages_zh_TW.properties`
- `src/main/resources/i18n/ordinals/labels_ar.properties`
- `src/main/resources/i18n/ordinals/labels_hi.properties`
- `src/main/resources/i18n/ordinals/labels_ur.properties`

## Suggested Contribution Workflow

1. Pick the locale file or files you want to improve.
2. Compare them against `src/main/resources/i18n/messages.properties`.
3. Update the translations carefully in the relevant `.properties` file.
4. Review the locale end-to-end for consistency instead of changing only one or two isolated strings.
5. If your language also has an ordinal labels file under `src/main/resources/i18n/ordinals/`, review that too.
6. Test the application if you can, so you can catch truncated text, awkward wording, and formatting issues in the UI.

## Submit Your Changes

Once your translation fixes are ready:

1. Commit your changes to your branch.
2. Open a merge request with the updated translation files.
3. Mention which locale(s) you translated or reviewed.
4. Include any notes about uncertain strings or places where developer context is needed.

If you are a native speaker and can help with any of the files above, please contribute a merge request.
