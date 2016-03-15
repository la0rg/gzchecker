package com.la0rg.vpog;

import org.jsoup.Connection;
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
import java.util.concurrent.*;

/**
 * Created by la0rg on 08.02.2016.
 */
public class Gzchecker {
    private final String site = "http://zakupki.gov.ru";
    private List<String> checks = new ArrayList<>();
    private final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public Gzchecker addCheck(String query) {
        checks.add(query);
        return this;
    }

    // Check all from dayFromStart to today, place every day to different folders
    public void checkAll(LocalDate dayFromStart) {
        LocalDate today = LocalDateTime.now().toLocalDate();
        if (dayFromStart.isAfter(today)) {
            throw new IllegalArgumentException("Specified day is after today.");
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        while (!dayFromStart.equals(today)) {
            String currentDayName = dayFromStart.format(FORMAT);
            String nextDayName = dayFromStart.plusDays(1).format(FORMAT);
            List<Future<CheckResult>> handles = new ArrayList<>();
            System.out.println("Проверяем день: " + currentDayName);
            try {
                StringBuilder log = new StringBuilder();
                for (int i = 0; i < checks.size(); i++) {
                    String check = checks.get(i);
                    log.append("Проверяем: " + check + "\r\n");
                    handles.add(executorService.submit(() -> checkOne(check, currentDayName, nextDayName, log)));
                }

                boolean hasResult = false;
                for (Future<CheckResult> h : handles) {
                    try {
                        CheckResult res = h.get();
                        List<String> htmls = res.getHtmls();
                        if (!htmls.isEmpty()) {
                            if (!hasResult) { // on first found result create folder of the date
                                hasResult = true;
                                new File(currentDayName).mkdir();
                            }
                            for (String html : res.getHtmls()) {
                                String fileName = currentDayName + File.separator + res.getName() + "-" + ThreadLocalRandom.current().nextInt(100_000, 999_999) + ".html";
                                log.append("Сохранение результата в файл: " + fileName + "\r\n");
                                File file = new File(fileName);
                                // Write file with result
                                PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                                writer.write(html);
                                writer.close();
                            }
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                // if at least one of the checks has result
                if (hasResult) {
                    // Write log file
                    PrintWriter printWriter = new PrintWriter(new File(currentDayName + File.separator + "Результаты за " + currentDayName + ".txt"));
                    printWriter.write(log.toString());
                    printWriter.close();
                    System.out.println("Результаты найдены.");
                } else {
                    System.out.println("Результаты не найдены.");
                    System.out.println(log.toString());
                }
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            dayFromStart = dayFromStart.plusDays(1);
        }
        executorService.shutdown();
    }

    private CheckResult checkOne(String name, String dateFrom, String dateTo, StringBuilder log) {
        System.out.println("Проверяем: " + name);
        List<String> res = new ArrayList<>();
        try {
            String url = site + "/epz/order/quicksearch/update.html?placeOfSearch=FZ_44&_placeOfSearch=on&placeOfSearch=FZ_223&_placeOfSearch=on&_placeOfSearch=on&priceFrom=0&priceTo=200+000+000+000&publishDateFrom=" +
                    dateFrom
                    + "&publishDateTo=" +
                    dateTo
                    + "&updateDateFrom=&updateDateTo=&orderStages=AF&_orderStages=on&orderStages=CA&_orderStages=on&orderStages=PC&_orderStages=on&_orderStages=on&sortDirection=false&sortBy=UPDATE_DATE&recordsPerPage=_50&pageNo=1&searchString=" +
                    URLEncoder.encode(name, "UTF-8")
                    + "&strictEqual=true&morphology=false&showLotsInfo=false&isPaging=false&isHeaderClick=&checkIds=";
            Connection.Response response = Jsoup.connect(url).timeout(0).execute();
            if (response.statusCode() != 200) {
                log.append("Сервер ответил с ошибкой.\r\n");
                return new CheckResult();
            }
            Document doc = response.parse();
            Elements elements = doc.select(".amountTenderTd a");
            if (elements.size() == 0) {
                log.append("Результатов не найдено.\r\n");
                return new CheckResult();
            }
            log.append("Найдено результатов: " + elements.size() + "\r\n");
            for (Element e : elements) {
                try {
                    String printUrl = e.attr("href");
                    if (!e.attr("href").startsWith("http")) {
                        printUrl = site + printUrl;
                    }
                    log.append("Получение: " + printUrl + "\r\n");
                    Document printForm = Jsoup.connect(printUrl).timeout(0).get();
                    String outerHtml = printForm.outerHtml();
                    if (outerHtml != null) {
                        res.add(outerHtml);
                        log.append("Получено успешно.\r\n");
                    } else {
                        log.append("Результат пустой.\r\n");
                    }
                } catch (IOException e1) {
                    log.append("ОШИБКА при получении результата.\r\n");
                    log.append(e1.getMessage());
                }
            }
        } catch (IOException e) {
            log.append("ОШИБКА поиска по запросу: " + name + ".\r\n");
            log.append(e.getMessage());
        }
        return new CheckResult(name, res);
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
        LocalDate dateFrom = LocalDate.now().minusDays(1);
        if (args.length != 0) {
            dateFrom = LocalDate.parse(args[0], DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
        gz.checkAll(dateFrom);
    }
}