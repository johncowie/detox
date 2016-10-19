(ns detox.examples-test
  (:require [detox.core :as c]
            [detox.validators :as v]
            [detox.test-utils :as tu :refer [is=]]
            ;[detox.translate :as t]
            [traversy.lens :as l]
            [detox.traversy :as t]
    #?(:clj
            [clojure.test :refer [deftest testing is are]]
       :cljs [cljs.test :refer-macros [deftest testing is are]])))

;; TODO, is there a way of making this work better?
(deftest minAge-maxAge-example
  (testing "that optional minAge and maxAge parameters can be parsed and compared"
    (let [->minAge [:minAge]
          ->maxAge [:maxAge]
          validator (c/chain
                      (c/group (c/optional-at v/coerce-integer :minAge ->minAge)
                               (c/optional-at v/coerce-integer :maxAge ->maxAge))
                      (c/optional-at v/a>b :compareAges ->maxAge ->minAge))
          translations {:minAge      {:coerce-integer "minAge parameter should be an integer, but was ~value~"}
                        :maxAge      {:coerce-integer "maxAge parameter should be an integer, but was ~value~"}
                        :compareAges {:a>b "maxAge should be greater than minAge"}}]
      ;; FIXME have way of indexing in templating
      (are [input output]
        (= (-> input (c/validate validator)) output)
        {} (tu/success-result {})
        {:minAge 18} (tu/success-result {:minAge 18})
        {:maxAge "bob"} (tu/single-error-result [:maxAge :coerce-integer] "bob")
        {:minAge "0.1" :maxAge "2"} (tu/single-error-result [:minAge :coerce-integer] "0.1")
        {:minAge "18" :maxAge "17"} (tu/single-error-result [:compareAges :a>b] [17 18])
        {:minAge 21 :maxAge "34"} (tu/success-result {:minAge 21 :maxAge 34})))))

(deftest validate-telephone
  (testing "can validate values in a map that should be telephone numbers"
    (let [->telephone-numbers (l/*> (l/in [:telephone]) l/all-values)
          phone-validator (c/chain v/mandatory v/is-string (v/matches-regex "[0-9]*"))
          validator (t/at phone-validator :telephone ->telephone-numbers)]
      (are [input output]
        (= (-> input (c/validate validator)) output)
        {} (tu/single-error-result [:telephone :mandatory] nil)
        {:telephone {}} (tu/single-error-result [:telephone :mandatory] nil)
        {:telephone {:mobile "01234567"}} (tu/success-result {:telephone {:mobile "01234567"}})
        {:telephone {:mobile 1244 :home "987623"}} (tu/single-error-result [:telephone :is-string] 1244 {})
        {:telephone {:home "tel01234" :mobile "123124"}} (tu/single-error-result [:telephone :matches-regex] "tel01234" {:regex "[0-9]*"})))))

;; FIXME have a check in selection for verifying that selector is of correct type e.g. travery as lens keys, path has vector
