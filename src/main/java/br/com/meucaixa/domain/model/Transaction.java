package br.com.meucaixa.domain.model;

import java.time.LocalDate;

public final class Transaction {
    public enum Type {
        INCOME("Receita"), EXPENSE("Despesa");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static Type fromLabel(String value) {
            for (Type type : values()) {
                if (type.label.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Tipo de lançamento inválido: " + value);
        }
    }

    private final long id;
    private final Type type;
    private final long amountCents;
    private final String category;
    private final String description;
    private final LocalDate date;

    public Transaction(long id, Type type, long amountCents, String category, String description, LocalDate date) {
        if (id <= 0) throw new IllegalArgumentException("O identificador deve ser positivo.");
        if (type == null) throw new IllegalArgumentException("Escolha receita ou despesa.");
        if (amountCents <= 0) throw new IllegalArgumentException("O valor deve ser maior que zero.");
        if (category == null || category.trim().isEmpty()) throw new IllegalArgumentException("Escolha uma categoria.");
        if (description == null || description.trim().isEmpty()) throw new IllegalArgumentException("Informe uma descrição.");
        if (date == null) throw new IllegalArgumentException("Informe uma data válida.");
        this.id = id;
        this.type = type;
        this.amountCents = amountCents;
        this.category = category.trim();
        this.description = description.trim();
        this.date = date;
    }

    public long getId() { return id; }
    public Type getType() { return type; }
    public long getAmountCents() { return amountCents; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public LocalDate getDate() { return date; }

    public long signedAmountCents() {
        return type == Type.INCOME ? amountCents : -amountCents;
    }
}
