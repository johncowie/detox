(ns detox.test-runner
  (:require [doo.runner :refer-macros [doo-tests doo-all-tests]]
            [detox.core-test]
            [detox.spike2-test]
            [detox.translate-test]
            [detox.traversy-test]
            [detox.validators-test]))

(doo-tests 'detox.core-test
           'detox.spike2-test
           'detox.translate-test
           'detox.traversy-test
           'detox.validators-test)

;(doo-all-tests) FIXME use this?