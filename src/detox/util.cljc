(ns detox.util)

(defn throw-error [s]
  #?(:clj  (throw (Exception. s))
     :cljs (throw (js/Error. s))))

#?(:cljs
   (defn parse-int-js [s]
     (let [v (js/parseFloat s)
           rem (mod v 1)]
       (if (and (number? v) (= rem 0))
         v
         (throw-error (str "Couln't parse integer from " s))))))

(defn parse-int [s]
  #?(:clj  (Integer. s)
     :cljs (parse-int-js s)))