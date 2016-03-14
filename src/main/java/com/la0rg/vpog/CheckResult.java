package com.la0rg.vpog;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by la0rg on 14.03.2016.
 */

public class CheckResult {
    private String name = "";
    private List<String> htmls = new ArrayList<>();

    public CheckResult(String name, List<String> htmls) {
        this.name = name;
        this.htmls = htmls;
    }

    public CheckResult() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getHtmls() {
        return htmls;
    }

    public void setHtmls(List<String> htmls) {
        this.htmls = htmls;
    }
}
