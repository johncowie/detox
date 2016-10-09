(ns detox.traversy-test
  (:require [detox.core :as c]
            [detox.validators :as v]
            [detox.traversy :as t]
            [detox.test-utils :as u]
            [traversy.lens :as l]
            #?(:clj [clojure.test :refer [deftest testing is]]))
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is]])))

(defn is= [a b]
  (is (= a b)))

(deftest traversy-selector-test
       (let [validator (-> v/mandatory (t/at :a (l/in [:a])))]
         (is=
           (c/validate {:a nil} validator)
           (u/single-error-result [:a :mandatory] nil))
         (is=
           (c/validate {:a 1} validator)
           (u/success-result {:a 1}))))
