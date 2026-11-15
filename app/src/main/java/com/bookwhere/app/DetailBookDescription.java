package com.bookwhere.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DetailBookDescription {
    // 말줄임표와 소수점은 문장 끝으로 세지 않고, 뒤따르는 닫는 따옴표와 괄호는 포함한다.
    private static final Pattern SENTENCE_END = Pattern.compile(
            "(?<![.…])(?:[.。]|[!?！？]+)[\\p{Pe}\\p{Pf}\"']*"
                    + "(?=[\\s\\p{Z}가-힣]|$)");

    static String completeSentences(String contents) {
        if (contents == null) return "";
        String text = contents.trim();
        Matcher endings = SENTENCE_END.matcher(text);
        int lastEnd = 0;
        while (endings.find()) {
            lastEnd = endings.end();
        }
        return text.substring(0, lastEnd);
    }
}
