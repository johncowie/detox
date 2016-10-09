(ns detox.test-runner
  (:require [doo.runner :refer-macros [doo-tests doo-all-tests]]))

;; TODO figure out how to exclude test namespaces
(doo-all-tests)