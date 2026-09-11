package com.cloud_guest.controller;

import com.cloud_guest.result.page.AbsPage;
import com.github.pagehelper.Page;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class PageCompatibilityTest {
    @Test
    void dtoConversionKeepsTheDatabaseTotalAndRequestedPage() {
        Page<Integer> rows = new Page<>(2, 10);
        rows.setTotal(25);
        rows.addAll(IntStream.rangeClosed(11, 20).boxed().toList());

        var result = new AbsPage() {}.mapPage(rows, value -> "uid-" + value);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(result.getList()).containsExactlyElementsOf(IntStream.rangeClosed(11, 20).mapToObj(value -> "uid-" + value).toList());
    }
}
