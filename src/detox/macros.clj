(ns detox.macros
  (:require [detox.core]))

(defmacro defvalidator [fn-name [v & other-args] & body]
  (if (empty? other-args)
    `(def ~fn-name
       (detox.core/base-validator
         ~(keyword fn-name)
         (fn [~v] ~@body)
         {}))
    `(defn ~fn-name [~@other-args]
       (detox.core/base-validator
         ~(keyword fn-name)
         (fn [~v] ~@body)
         ~(zipmap (map keyword other-args) other-args)))))

(defmacro defpredicate [fn-name [v & other-args] & body]
  (if (empty? other-args)
    `(def ~fn-name
       (detox.core/base-validator
         ~(keyword fn-name)
         (fn [~v] (if ~@body (detox.core/success-value ~v) (detox.core/error-value ~v)))
         {}))
    `(defn ~fn-name [~@other-args]
       (detox.core/base-validator
         ~(keyword fn-name)
         (fn [~v] (if ~@body (detox.core/success-value ~v) (detox.core/error-value ~v)))
         ~(zipmap (map keyword other-args) other-args)))))