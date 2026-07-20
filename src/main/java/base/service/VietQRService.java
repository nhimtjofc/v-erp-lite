package base.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;


import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

@Slf4j
@ApplicationScoped
public class VietQRService {

    private static final Map<String, String> BANK_BINS = new HashMap<>();

    static {
        BANK_BINS.put("VCB", "970436");
        BANK_BINS.put("TCB", "970407");
        BANK_BINS.put("ACB", "970416");
        BANK_BINS.put("VTB", "970415"); // Legacy
        BANK_BINS.put("ICB", "970415"); // VietinBank
        BANK_BINS.put("BIDV", "970418");
        BANK_BINS.put("MBB", "970422"); // Legacy
        BANK_BINS.put("MB", "970422");  // MBBank
        BANK_BINS.put("VPB", "970432");
        BANK_BINS.put("TPB", "970423");
        BANK_BINS.put("STB", "970403");
        BANK_BINS.put("HDB", "970437");
        BANK_BINS.put("VBA", "970405"); // Agribank
        BANK_BINS.put("VIB", "970441"); // VIB
        BANK_BINS.put("SHB", "970443"); // SHB
        BANK_BINS.put("EIB", "970431"); // Eximbank
        BANK_BINS.put("MSB", "970426"); // MSB
        BANK_BINS.put("OCB", "970448"); // OCB
        BANK_BINS.put("LPB", "970449"); // LPBank
        BANK_BINS.put("SGB", "970400"); // Saigonbank
        BANK_BINS.put("BAB", "970409"); // Bac A Bank
        BANK_BINS.put("NAB", "970428"); // Nam A Bank
    }

    public String generateQrPayload(String bankCode, String accNo, long amount, String message) {
        String bin = BANK_BINS.get(bankCode.toUpperCase());
        if (bin == null) {
            throw new jakarta.ws.rs.WebApplicationException(
                jakarta.ws.rs.core.Response.status(400)
                .entity(java.util.Map.of("message", "Unsupported bank code: " + bankCode))
                .build()
            );
        }

        String cleanMessage = removeAccents(message);
        if (cleanMessage != null && cleanMessage.length() > 50) {
            cleanMessage = cleanMessage.substring(0, 50);
        }

        String beneficiary = buildTLV("00", bin) + buildTLV("01", accNo);
        String tag38Value = buildTLV("00", "A000000727") + buildTLV("01", beneficiary) + buildTLV("02", "QRIBFTTA");

        StringBuilder payload = new StringBuilder();
        payload.append(buildTLV("00", "01")); // Format
        payload.append(buildTLV("01", amount > 0 ? "12" : "11")); // Initiation
        payload.append(buildTLV("38", tag38Value)); // Merchant Account Info
        payload.append(buildTLV("53", "704")); // Currency VND
        if (amount > 0) {
            payload.append(buildTLV("54", String.valueOf(amount)));
        }
        payload.append(buildTLV("58", "VN")); // Country
        
        if (cleanMessage != null && !cleanMessage.trim().isEmpty()) {
            String tag62Value = buildTLV("08", cleanMessage);
            payload.append(buildTLV("62", tag62Value));
        }
        
        payload.append("6304");
        String crc = calculateCRC(payload.toString());
        payload.append(crc);

        return payload.toString();
    }

    public String generateQrBase64(String bankCode, String accNo, long amount, String message) {
        try {
            String payload = generateQrPayload(bankCode, accNo, amount, message);

            // Render with ZXing
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = qrCodeWriter.encode(payload, BarcodeFormat.QR_CODE, 400, 400, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            log.error("Failed to generate VietQR", e);
            throw new RuntimeException("Failed to generate VietQR", e);
        }
    }

    private String buildTLV(String tag, String value) {
        String length = String.format("%02d", value.length());
        return tag + length + value;
    }

    private String calculateCRC(String data) {
        int crc = 0xFFFF;
        int polynomial = 0x1021; // CCITT
        for (byte b : data.getBytes()) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) {
                    crc ^= polynomial;
                }
            }
        }
        crc &= 0xffff;
        return String.format("%04X", crc);
    }

    private String removeAccents(String str) {
        if (str == null) return null;
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD); 
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccents = pattern.matcher(nfdNormalizedString).replaceAll("");
        return noAccents.replaceAll("Đ", "D").replaceAll("đ", "d");
    }
}
