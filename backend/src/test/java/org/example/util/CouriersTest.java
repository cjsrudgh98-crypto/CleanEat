package org.example.util;

import org.example.shipping.Couriers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouriersTest {

    @Test
    void 목록에_있는_택배사는_조회_링크를_만든다() {
        assertThat(Couriers.trackingUrl("CJ대한통운", "6123-4567-8901"))
                .isEqualTo("https://trace.cjlogistics.com/next/tracking.html?wblNo=612345678901");
    }

    @Test
    void 모르는_택배사나_운송장이_없으면_링크가_없다() {
        assertThat(Couriers.trackingUrl("동네택배", "123")).isNull();
        assertThat(Couriers.trackingUrl("CJ대한통운", " ")).isNull();
        assertThat(Couriers.trackingUrl(null, "123")).isNull();
    }
}
