package org.example.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenFoodFactsResponse {

    private int status;

    @JsonProperty("product")
    private ProductData product;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductData {

        @JsonProperty("product_name")
        private String productName;

        @JsonProperty("product_name_ko")
        private String productNameKo;

        @JsonProperty("ingredients_text")
        private String ingredientsText;

        @JsonProperty("ingredients_text_ko")
        private String ingredientsTextKo;

        @JsonProperty("ingredients_text_en")
        private String ingredientsTextEn;
    }
}
