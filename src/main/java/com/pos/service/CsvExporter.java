package com.pos.service;

import com.pos.model.Sale;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CsvExporter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvExporter() {
    }

    public static void writeSales(Path file, List<Sale> sales) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(65279);
            out.write("receipt,time,cashier,payment,items,total");
            out.newLine();

            for (Sale sale : sales) {
                out.write(
                    String.join(
                        ",",
                        escape(sale.getReceiptNo()),
                        escape(sale.getSaleTime().format(TIME)),
                        escape(sale.getCashierName()),
                        escape(sale.getPaymentMethod().label()),
                        String.valueOf(sale.itemCount()),
                        sale.getTotal().toPlainString()
                    )
                );
                out.newLine();
            }
        }
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        } else {
            return !value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")
                ? value
                : "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }
}
