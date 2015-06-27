(ns tt-clj-bot.driver
  (:require [tt-clj-bot.httpc :as tale]
            [tt-clj-bot.logic :as logic]
            [clojure.tools.logging :as logger])
  (:use [slingshot.slingshot :only [throw+ try+]]))


(defmacro log [level & data]
  (let [frm (first (filter vector? data))
        result (gensym "result")
        log-args (replace {frm result} data)
        log-cmd (symbol (str "logger/" (name level)))]
    `(let [~result ~(first frm)]
       ;(println ~@log-args)
       (~log-cmd ~@log-args)
       ~result)))

(def ^:private +error-wait-time+ 30)
(def ^:private +interleave-wait-time+ 1)

(defn- wait [seconds]
  (Thread/sleep (* seconds 1000)))

(def ^:private +supported-action-types+
  #{:login :api-info :logout :game-info
    :get-card :use-ability :combine-cards :use-card})

(defn- valid-action? [session action-type]
  ;(print session action-type)
  (contains? (:api-settings (if session session (tale/make-session))) action-type))
;  (contains? +supported-action-types+ action-type))

(defn- the-alias [name] (get (ns-aliases *ns*) name))

(defn- from-action-type [session action-type]
;;; TODO: Find why the commented line does work in repl
;;; And doen't in application
  (when (valid-action? session action-type)
    ;(println (->> action-type name symbol (ns-resolve (the-alias 'tale))))
    (->> action-type name symbol (ns-resolve 'tt-clj-bot.httpc))))

(defn- apply-action [session action]
  (let [action-fn (from-action-type session (first action))
        arguments (rest action)]
    (when (nil? action-fn)
      (throw+ {:type ::action-error :message (str "Wrong action: " (first action))}))
    (try+
     (if (nil? session)
       (apply action-fn (tale/make-session) arguments)
       (apply action-fn session arguments))
     (catch clojure.lang.ArityException e
       (throw+ {:type ::action-error :message (:message &throw-context)
                :session session})))))

(defmulti process-action
  (fn [session action]
    (cond
      (integer? action) :wait
      (and (sequential? action) (= :login)) :login ;may differ from usual action
      (sequential? action) :act
      :else :default)))

(defmethod process-action :wait
  [session action]
  (log :debug "Waiting " [action] " seconds")
  (wait action)
  session)

(defmethod process-action :act
  [session action]
  (log :debug "Processing action: " [action])
  (log :debug "With session: " [session])
  (log :debug "Result: " [(apply-action session action)]))

(defmethod process-action :login
  [session action]
  (apply-action session action))

(defmethod process-action :default
  [session action]
  (log :info "Wrong action: " action ". Waiting for " +error-wait-time+ " seconds.")
  (process-action session +error-wait-time+))

(defn- logout [session]
  (log :info "Nothing to do with" [session])
  (when (not (nil? session))
    (log :info "Logout...")
    (process-action session [:logout])))

(defn- actions-hide-private-info [actions]
  (mapv #(if (and (sequential? %)
                  (= (first %) :login))
           [:login "*hidden-email*" "*hidden-password*"]
           %) actions))

(defn wait-retry
  ([]
   (wait-retry +error-wait-time+))
  ([seconds]
   (log :info "Retrying in " seconds " seconds.")
   (process-action nil seconds)
   true))

(defn run
  "Argument: function that accepts vector of 2 elements:
  1) a logic function
  2) meta info about last logic load.
  Return: [new-logic-fn new-meta]"
  [logic-loader]
  (let [recur?
        (try+
         (try+

          (log :info "Starting bot...")
          (loop [session nil
                 ll-args [nil 0]]
            (let [[logic-fn _ :as ll-res] (logic-loader ll-args)
                  {:keys [result output]} (logic-fn session)
                  actions (interleave result (repeat +interleave-wait-time+))]
              (log :info "Bot log: " output)
              (log :info "Actions: " (actions-hide-private-info actions))
              (if (empty? actions)
                (logout session)
                (recur (reduce process-action session actions) ll-res))))
          (log :info "Stoping bot...")

          (catch [:type :tt-clj-bot.logic/timeout-error] {:keys [message session]}
            (log :info "Execution Warning!")
            (log :info message)
            (logout session)
            (wait-retry))
          (catch [:type :tt-clj-bot.driver/action-error] {:keys [message session]}
            (log :info "Action Exception!")
            (log :info message)
            (logout session)
            (wait-retry)))
         (catch [:type :tt-clj-bot.logic/execution-error] {:keys [message session]}
           (log :info "Execution error in bot source.")
           (log :info message)
           (logout session)
           (wait-retry))
         (catch java.net.SocketException e
           (log :info "Socket Exception!")
           (log :info "Exception: " e)
           (log :info &throw-context)
           (log :info "StackTrace:")
           (log :info  "\n" (interpose "\n" (map str (:stack-trace &throw-context))))
           (wait-retry)))]
    (when recur?
      (recur logic-loader))))




