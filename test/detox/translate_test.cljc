(ns detox.translate-test
  (:require [detox.translate :as t]
            [detox.test-utils :as u]
            #?(:clj [clojure.test :refer [deftest testing is]]))
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is]])))

(defn is= [a b]
  (is (= a b)))

(deftest translate-test
  (testing "translating validation errors"
    (testing "translating one error, one level deep"
      (let [errors (u/error-result (u/single-error [:greater-than] 1 {:limit 3}))
            translations {:greater-than "Value ~value~ was not greater than ~~limit~~"}]
        (is=
          (t/translate errors translations)
          {:greater-than "Value 1 was not greater than 3"})))
    (testing "translating one error, multiple levels deep"
      (let [errors (u/error-result (u/single-error [:age :less-than] 20 {:limit 18}))
            translations {:age {:less-than "Person is ~value~, not under ~~limit~~"}}]
        (is= (t/translate errors translations)
             {:age {:less-than "Person is 20, not under 18"}})))
    (testing "translating multiple errors, nested"
      (let [errors (u/error-result (u/single-error [:age :less-than] 20 {:limit 15})
                                   (u/single-error [:age :mandatory] nil {})
                                   (u/single-error [:name :first-name :equal-to] "Derek" {:name "Joe"}))
            translations {:age  {:less-than "Person is ~value~, not under ~~limit~~"
                                 :mandatory "Person must have an age"}
                          :name {:first-name {:equal-to "Person's first name must be ~~name~~, but was ~value~"}}}]
        (is=
          (t/translate errors translations)
          {:age  {:less-than "Person is 20, not under 15"
                  :mandatory "Person must have an age"}
           :name {:first-name {:equal-to "Person's first name must be Joe, but was Derek"}}})))
    #_(future-fact "can translate error that has multiple values in list") ;; FIXME do this soon
    ))

(deftest check-translations-test
  (testing "checking that no translations are missing or superfluous"
    (let [errors (u/error-result (u/single-error [:greater-than] 1 {:limit 3})
                                 (u/single-error [:age :less-than] 20 {:limit 18})
                                 (u/single-error [:age :mandatory] nil {}))]
      (testing "finding missing translations"
        (let [translations {:age {:mandatory "Age is mandatory"}}]
          (is=
            (t/check-translations errors translations)
            {:missing     [[:age :less-than]
                           [:greater-than]]
             :superfluous []})))
      (testing "finding superfluous translations"
        (let [translations {:age          {:less-than "Age less than message"
                                           :mandatory "Age mandatory message"}
                            :greater-than "Greater than message"
                            :name         {:first-name "Some other message"
                                           :last-name  "Some other message"}}]
          (is=
            (t/check-translations errors translations)
            {:missing     []
             :superfluous [[:name :first-name]
                           [:name :last-name]]}))))))
(deftest initialise-translations-test
  (testing "initialising translations map from a list of errors"
    (testing "single error"
      (let [errors (u/error-result (u/single-error [:greater-than] "v" {:limit 3}))]
        (is=
          (t/initialise-translations errors)
          {:greater-than "~value~ ~~limit~~"})))
    (testing "multiple errors"
      (let [errors (u/error-result (u/single-error [:age :less-than] "v" {:limit 20})
                                   (u/single-error [:age :in-between] "v" {:lower 10 :upper 20})
                                   (u/single-error [:name :first-name :equal-to] "v" {:name "Joe"}))]
        (is=
          (t/initialise-translations errors)
          {:age  {:less-than  "~value~ ~~limit~~"
                  :in-between "~value~ ~~lower~~ ~~upper~~"}
           :name {:first-name {:equal-to "~value~ ~~name~~"}}})))))