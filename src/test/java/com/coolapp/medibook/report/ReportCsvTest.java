package com.coolapp.medibook.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCsvTest {

    @Test
    void escapesAndNeutralisesFormulas() {
        assertThat(ReportService.csv("plain")).isEqualTo("plain");
        assertThat(ReportService.csv("a,b")).isEqualTo("\"a,b\"");
        assertThat(ReportService.csv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportService.csv("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(ReportService.csv(null)).isEmpty();
    }
}
