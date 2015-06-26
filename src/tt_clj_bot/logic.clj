(ns tt-clj-bot.logic
  (:require [clojail.core :as jail]
            [clojail.testers :as guards])
  (:use [slingshot.slingshot :only [throw+ try+]]))


(defn setup-jvm-default
  "This functions setups java security file that should
  be located in ./resources/java.security.
  Alternatively you can pass pasrameters to java line args."
  []
  (System/setProperty "java.security.policy", "./resources/java.security"))

(def ^:dynamic *session* nil)

(defn make-logic
  "Input: very restricted clojure code. This code should return a vector
  of actions [[:game-info] [:use :help] 40 [:game-info 17721] [:game-info]].
  Action is a vector or number.
  If action is a vector,
  then the first part is a keyword from httpc API (like :game-info)
  and rest are parameters to api method.
  If action is an integer number, then driver will wait this amount of seconds
  and perform next action.
  You can use *session* to access the session data.

  Result: function that requires session as argument. It returns a map
  {:result <result of restrincted clojure code>
  :output <string of outputs of restricted clojure code that will be logged>}"
  [logic-code email passwd]
  (let [ns-name 'sandbox
        sb-ns (create-ns ns-name)
        curr-ns *ns*]
    (binding [*ns* sb-ns]
      (use '[tt-clj-bot.logic :only (*session*)]))
      (intern sb-ns '*email* email)
      (intern sb-ns '*passwd* passwd)
    (let [sb (jail/sandbox guards/secure-tester :namespace ns-name)]
      (fn [session]
        (try+
         (let [out (java.io.StringWriter.)]
           {:result
            (sb logic-code {#'*session* session #'*out* out})
            :output
            (str out)})
         (catch java.util.concurrent.ExecutionException _
           (throw+ {:type ::execution-error :message (:message &throw-context)})))))))





