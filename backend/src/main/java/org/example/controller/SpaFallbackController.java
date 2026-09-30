package org.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * React Router가 클라이언트에서 처리하는 경로들. 새로고침/직접 접속 시 서버가 이 경로들을 몰라서
 * 404를 내는 대신 index.html로 forward해서 React가 뜬 뒤 라우팅을 이어받게 한다.
 */
@Controller
public class SpaFallbackController {

    @RequestMapping({"/cart", "/checkout", "/orders", "/orders/{orderId}", "/products", "/products/{productId}", "/mypage",
            "/payments/success", "/payments/fail", "/admin", "/admin/{tab}", "/stats", "/favorites", "/ingredients"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
