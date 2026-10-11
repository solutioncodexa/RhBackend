package ma.rhflow.util;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class XlsxFiles {

    private XlsxFiles() {
    }

    public static byte[] table(String sheetName, String[] headers, List<String[]> rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet(sheetName == null || sheetName.isBlank() ? "Export" : sheetName);
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i] == null ? "" : headers[i]);
            }
            int index = 1;
            for (String[] row : rows) {
                Row line = sheet.createRow(index++);
                for (int i = 0; i < row.length; i++) {
                    line.createCell(i).setCellValue(row[i] == null ? "" : row[i]);
                }
            }
            book.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
