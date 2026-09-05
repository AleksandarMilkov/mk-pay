package com.github.aleksandarmilkov.mkpay.domain;

public enum PaymentState {
    PENDING,
    DEBIT_INITIATED,
    DEBIT_SUCCESS,
    CREDIT_INITIATED,
    COMPLETED,
    FAILED
}