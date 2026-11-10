/**
 * Утилитный класс для вычисления контрольных сумм ASCII-символов.
 * Лабораторная работа №3 (Вариант 3)
 * Автор: Цибульскас Виктор Сергеевич
 *
 * Допускаются только ASCII-символы (коды 0..127). Для ASCII-символа
 * код совпадает с его единственным байтом, поэтому сумма кодов равна
 * сумме байтов ASCII-символов.
 */
public final class ChecksumUtil {

    public static final int CHUNK_SIZE = 10;

    private ChecksumUtil() {}

    /**
     * Проверяет, что строка состоит только из ASCII-символов (коды 0..127).
     */
    public static boolean isAscii(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    /**
     * Вычисляет сумму ASCII-кодов (байтов) символов цепочки.
     * Для не-ASCII цепочки выбрасывается IllegalArgumentException.
     */
    public static int calculateAsciiChecksum(String chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Цепочка символов не может быть null");
        }

        int sum = 0;
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (c > 0x7F) {
                throw new IllegalArgumentException(
                        "Допускаются только ASCII-символы (коды 0..127), найден символ с кодом " + (int) c);
            }
            sum += c;
        }
        return sum;
    }

    /**
     * Формирует подробную детализацию вычисления суммы ASCII-кодов.
     * Печатные ASCII-символы показываются как 'a'=97, остальные — как 0xNN=NN.
     */
    public static String formatCalculationDetails(String chunk) {
        if (chunk == null) return "";

        StringBuilder sb = new StringBuilder();
        int sum = 0;
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (c > 0x7F) {
                throw new IllegalArgumentException(
                        "Допускаются только ASCII-символы (коды 0..127), найден символ с кодом " + (int) c);
            }

            int code = c;
            sum += code;
            if (code >= 32 && code < 127) {
                sb.append('\'').append(c).append("'=").append(code);
            } else {
                sb.append(String.format("0x%02X=%d", code, code));
            }
            if (i < chunk.length() - 1) {
                sb.append(" + ");
            }
        }
        sb.append(" = ").append(sum);
        return sb.toString();
    }
}
