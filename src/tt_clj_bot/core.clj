(ns tt-clj-bot.core
  (:gen-class)
  (:require [tt-clj-bot.driver :as driver]
            [clojure.tools.logging :as logger])
  (:use [tt-clj-bot.logic :only [setup-jvm-default make-logic]]
        [tt-clj-bot.driver :only [run]]
        [slingshot.slingshot :only [try+]]
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

(defn print-help [help-data]
  (map #(println (reduce str (repeat (first %) " "))
                 (second %)) +help-data+))

(defn assert-argcount [args]
  (when (not (or (-> args count (= 1))
                 (-> args count (= 2))))
    (print-help +help-data+)
    (System/exit 1)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (assert-argcount args)
  (try+
   (setup-jvm-default)
   (let [account (when (= (count args) 2)
                   (-> args second slurp read-string))]
     (-> args
         first
         slurp
         read-string
         (make-logic (:email account) (:password account))
         run))
   (catch java.io.FileNotFoundException _
     (print-help +help-data+)
     (logger/error "Specify accessible files."))
   (catch [:type :tt-clj-bot.logic/execution-error] {:keys [message]}
     (logger/error "Execution error in bot source. Fixbot!")
     (logger/error message))
   (catch Object e
     (logger/error "Exception: " e)
     (logger/error &throw-context)
     (logger/error "StackTrace:")
     (logger/error  "\n" (interpose "\n" (map str (:stack-trace &throw-context)))))))




