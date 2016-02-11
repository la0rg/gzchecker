package com.la0rg.vpog;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by la0rg on 08.02.2016.
 */
public class Gzchecker {
    private String site = "http://zakupki.gov.ru";
    private List<String> checks = new ArrayList<>();

    public Gzchecker addCheck(String query) {
        checks.add(query);
        return this;
    }

    public void checkAll() {
        // Creating folder for results
        LocalDate ldate = LocalDateTime.now().toLocalDate().minusDays(1); //start search from the previous day
        String dirName = ldate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        new File(dirName).mkdir();
        try {
            PrintWriter log = new PrintWriter(new File(dirName + File.separator + "Результаты за " + dirName + ".txt"));
            for (String check : checks) {
                log.write("Проверяем: " + check + "\n\r");
                System.out.println("Проверяем: " + check);
                List<String> htmls = checkOne(check, dirName, log);
                for (String html : htmls) {
                    String fileName = dirName + File.separator + check + "-" + ThreadLocalRandom.current().nextInt(100_000, 999_999) + ".html";
                    log.write("Сохранение результата в файл: " + fileName + "\n\r");
                    File file = new File(fileName);
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                    writer.write(html);
                    writer.close();
                }
            }
            log.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private List<String> checkOne(String name, String date, PrintWriter log) {
        List<String> res = new ArrayList<>();
        try {
            String url = site + "/epz/order/quicksearch/update.html?placeOfSearch=FZ_44&_placeOfSearch=on&placeOfSearch=FZ_223&_placeOfSearch=on&_placeOfSearch=on&priceFrom=0&priceTo=200+000+000+000&publishDateFrom=" +
                    date
                    + "&publishDateTo=&updateDateFrom=&updateDateTo=&orderStages=AF&_orderStages=on&orderStages=CA&_orderStages=on&_orderStages=on&_orderStages=on&sortDirection=false&sortBy=UPDATE_DATE&recordsPerPage=_50&pageNo=1&searchString=" +
                    URLEncoder.encode(name, "UTF-8")
                    + "&strictEqual=false&morphology=false&showLotsInfo=false&isPaging=false&isHeaderClick=&checkIds=";
            //log.write("Поиск: " + url + "\n\r");
            Document doc = Jsoup.connect(url).timeout(10000).get();
            Elements elements = doc.select(".amountTenderTd a");
            if (elements.size() == 0) {
                log.write("Результатов не найдено.\n\r");
                return res;
            }
            log.write("Найдено результатов: " + elements.size() + "\n\r");
            for (Element e : elements) {
                try {
                    String printUrl = e.attr("href");
                    if (!e.attr("href").startsWith("http")) {
                        printUrl = site + printUrl;
                    }
                    log.write("Получение: " + printUrl + "\n\r");
                    Document printForm = Jsoup.connect(printUrl).timeout(10000).get();
                    String outerHtml = printForm.outerHtml();
                    if (outerHtml != null) {
                        res.add(outerHtml);
                        log.write("Получено успешно.\n\r");
                    } else {
                        log.write("Результат пустой.\n\r");
                    }
                } catch (IOException e1) {
                    log.write("ОШИБКА при получении результата.\n\r");
                    log.write(e1.getMessage());
                }
            }
        } catch (IOException e) {
            log.write("ОШИБКА поиска по запросу: " + name + ".\n\r");
            log.write(e.getMessage());
        }
        return res;
    }

    public static void main(String[] args) {
        // try to set console encoding for system.out
        String consoleEncoding = System.getProperty("consoleEncoding");
        if (consoleEncoding != null) {
            try {
                System.setOut(new PrintStream(System.out, true, consoleEncoding));
            } catch (java.io.UnsupportedEncodingException ex) {
                System.err.println("Unsupported encoding set for console: " + consoleEncoding);
            }
        }

        Gzchecker gz = new Gzchecker();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File("gz.config")), "UTF-8"))) {
            for (String line; (line = br.readLine()) != null; ) {
                gz.addCheck(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        gz.checkAll();
    }
}
