package edu.itc.shop.domain.order;

/** NEW → PAID, or NEW → CANCELLED. Nothing leaves PAID or CANCELLED. */
public enum OrderStatus { NEW, PAID, CANCELLED }
