(ns tt-clj-bot.core
  (:gen-class)
  (:require [tt-clj-bot.driver :as driver]
            [clojure.tools.logging :as logger])
  (:use [tt-clj-bot.logic :only [setup-jvm-default make-logic]]
        [tt-clj-bot.driver :only [run]]
        [slingshot.slingshot :only [try+ throw+]]
        ))

(def ^:private +help-data+
  [[0 "Usage:"]
   [4 "java -jar <tt-clj-bot-jar> <bot-program> <account-file>"]
   [4 "Example account file: {:email \"foo@bar.moo\", :password \"pa$$w0rd\"}"]
   [4 "Make sure to have java.security file in the ./resourse/ directory!"]
   [0 ""]
   [4 "ATTENTION: password is stored in memory unencrypted at this moment."]
   [4 "ATTENTION: password may appear in log file."]
   [4 "ATTENTION: make sure that you is the only one who can read logs and output."]]
  )

(defn help-string [help-data]
  (reduce str (map #(str (reduce str (-> % first (repeat " ")))
                         (second %) "\n")
                   help-data)))

(defn assert-argcount [args]
  (when (or (= (first args) "--help") (= (first args) "-h"))
    (throw+ {:type ::command-line-error :message (help-string +help-data+)}))
  (when (not (or (-> args count (= 1))
                 (-> args count (= 2))))
    (throw+ {:type ::command-line-error :message (help-string +help-data+)})))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (try+
   (try+
    (assert-argcount args)
    (setup-jvm-default)
    (let [account (when (= (count args) 2)
                    (-> args second slurp read-string))]
      (-> args
          first
          slurp
          read-string
          (make-logic (:email account) (:password account))
          run))
    (catch [:type :tt-clj-bot.core/command-line-error] {:keys [message]}
      (println message))
    (catch java.io.FileNotFoundException _
      (logger/error (:message &throw-context)))
    (catch [:type :tt-clj-bot.logic/execution-error] {:keys [message]}
      (logger/error "Execution error in bot source.")
      (logger/error message))
    (catch java.lang.RuntimeException e
      (if (= (:message &throw-context) "EOF while reading")
        (do (logger/error "Execution error in bot/account source.")
            (logger/error (:message &throw-context))
            (logger/error "Perhaps you have parentheses mismatched."))
        (throw+ e)))
   (catch Object e
     (logger/error "Exception: " e)
     (logger/error &throw-context)
     (logger/error "StackTrace:")
     (logger/error  "\n" (interpose "\n" (map str (:stack-trace &throw-context))))))))




