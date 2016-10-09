(ns detox.monad)

(defprotocol Functor
  (fmap [x f]))

(defprotocol Monad
  ;; (m a) -> (a -> m a) -> m b
  (bind [x f])
  ;; a -> (m a)
  (return [x]))

;(defprotocol Monoid
;  (mappend )
;  )

; (Monad m, Traversable t) => t (m a) -> m (t a)
(defn msequence [])

(defprotocol Result
  (success? [this])
  (result-value? [this])
  (prefix [this id]))

(defrecord EitherResult [result value]
  Result
  (success? [_] (= result :success))
  (result-value? [_] value)
  (prefix [this id])
  Monad
  (bind [this f]
    this)
  (return [this]))


