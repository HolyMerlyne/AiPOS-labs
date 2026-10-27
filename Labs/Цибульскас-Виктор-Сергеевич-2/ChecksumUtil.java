import java.nio.charset.StandardCharsets;

/**
 * Утилитный класс для вычисления контрольных сумм символов ASCII.
 * Лабораторная работа №3 (Вариант 3)
 * Автор: Цибульскас Виктор Сергеевич
 *
 * Сумма считается по байтам UTF-8. Для ASCII-символов (коды 0..127)
 * это совпадает с суммой их ASCII-кодов, а для не-ASCII символов
 * учитываются реальные байты переданного потока, а не коды UTF-16.
 */
public final class ChecksumUtil {

    public static final int CHUNK_SIZE = 10;

    private ChecksumUtil() {}

    /**
     * Вычисляет контрольную сумму байтов цепочки.
     * Для ASCII-цепочек результат равен сумме ASCII-кодов символов.
     */
    public static int calculateAsciiChecksum(String chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Цепочка символов не может быть null");
        }
        int sum = 0;
        for (byte value : chunk.getBytes(StandardCharsets.UTF_8)) {
            sum += value & 0xFF;
        }
        return sum;
    }

    /**
     * Формирует подробную детализацию вычисления суммы.
     * Печатные ASCII-байты показываются как 'a'=97, остальные — как 0xNN=NN.
     */
    public static String formatCalculationDetails(String chunk) {
        if (chunk == null) return "";

        byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        int sum = 0;
        for (int i = 0; i < bytes.length; i++) {
            int code = bytes[i] & 0xFF;
            sum += code;
            if (code >= 32 && code < 127) {
                sb.append('\'').append((char) code).append("'=").append(code);
            } else {
                sb.append(String.format("0x%02X=%d", code, code));
            }
            if (i < bytes.length - 1) {
                sb.append(" + ");
            }
        }
        sb.append(" = ").append(sum);
        return sb.toString();
    }
}
