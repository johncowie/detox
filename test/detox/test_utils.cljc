(ns detox.test-utils
  (:require
    #?(:clj [clojure.test :refer [is]]
       :cljs [cljs.test :refer-macros [is]])))

(defn is= [a b] (is (= a b)))

(defn single-error
  ([id value constraints]
   {:type id :value value :constraints constraints})
  ([id value] (single-error id value {})))

(defn add-error-to-map [m {:keys [type value constraints]}]
  (update-in m type concat [{:value value :constraints constraints}]))

(defn convert-errors-to-map [errors]
  (reduce add-error-to-map {} errors))

(defn error-result [& errors]
  {:result :error :value (or errors [])})

(def single-error-result (comp error-result single-error))

(defn success-result [value]
  {:result :success :value value})

(defn short-circuit-result [value]
  {:result :break :value value})

(defmacro future-testing [string & body]
  `(println "FUTURE_TEST: " ~string))

;; midje like syntax
;
;(declare rewrite-body)
;
;(defn gen-facts [body]
;  (cond (not (sequential? body)) [body]
;        (empty? body) []
;        :else (let [[f s t & r] body]
;                (if (= s '=>)
;                  (if (> (count body) 2)
;                    (cons `(clojure.test/is (= ~f ~t)) (gen-facts r))
;                    (throw (Exception. "Something wrong with fact")))
;                  (concat (gen-facts (first body)) (gen-facts (rest body)))))))
;
;(gen-facts '(fact ""))
;
;(defn rewrite-body [description body]
;  (let [b (gen-facts body)]
;    `(clojure.test/testing ~description
;       ~@b
;       )))
;
;(defn rewrite-test [description body]
;  (let [b (gen-facts body)
;        name (symbol description)]
;    `(clojure.test/deftest ~name
;       ~@b)))
;
;(defmacro facts [description & body]
;  (let [name (symbol (clojure.string/replace description #"\s+" "-"))]
;    `(clojure.test/deftest ~name ~@body)))
;
;(defmacro fact [description & body]
;  (rewrite-body description body))
;
;(macroexpand '(facts "blah"
;                     (fact "bb" 3 => 4)
;                     ))

