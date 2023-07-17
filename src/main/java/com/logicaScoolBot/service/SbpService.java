package com.logicaScoolBot.service;

import com.logicaScoolBot.entity.Qr;

import java.util.List;

public interface SbpService {

    /**
     * Регистрация QR-кода в НСПК и получение ссылки на QR
     *
     * @param amount  сумма
     * @param purpose
     * @return Ссылка на QR-код
     */
    String registerQr(int amount, String purpose);

    List<String> getQrStatus(List<String> qrcIdNotStartedList);

    List<String> statusQr();

    List<Qr> getAllByQRId(List<String> qrcId);

//    Qr save(Qr qr);
//
//    Qr update(Qr qr);
}