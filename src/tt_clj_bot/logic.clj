(ns tt-clj-bot.logic
  (:require [clojail.core :as jail]
            [clojail.testers :as guards]
            [clj-time.core :as t])
  (:use [slingshot.slingshot :only [throw+ try+]]))


(defn setup-jvm-default
  "This functions setups java security file that should
  be located in ./resources/java.security.
  Alternatively you can pass pasrameters to java line args."
  []
  (System/setProperty "java.security.policy", "./resources/java.security"))

(def ^:dynamic *session* nil)

(defn- interval-seconds [from to]
  ;(println from to)
  (t/in-seconds (t/interval from to)))

(defn- change-update-time [dict now]
  (when dict
    ;(println dict)
    ;(println now)
    (assoc dict
           :update-time (interval-seconds (:update-time dict) now))))

(defn- prepare-update-time [session]
  (when session
    (let [now (t/now)
          seconds-past (fn [t1] (interval-seconds t1 now))
          change-up-time (fn [[key vdict]]
                           [key (change-update-time vdict now)])]
      (assoc session
             :session-created (seconds-past (:session-created session))
             :update-time (seconds-past (:update-time session))
             :body (when (:body session)
                     (into {} (map change-up-time (:body session))))))))

(def +short-vars+
  {'*api-info* #(get-in % [:body :api-info])
   '*login* #(get-in % [:body :login])
   '*logout* #(get-in % [:body :logout])
   '*game-info* #(get-in % [:body :game-info])
   '*account-info* #(get-in % [:body :account-info])
   '*places-list* #(get-in % [:body :places-list])
   '*place-info* #(get-in % [:body :place-info])
   '*hero-account* #(get-in % [:body :game-info :data :account])
   '*hero-info* #(get-in % [:body :game-info :data :account :hero])
   '*enemy-account* #(get-in % [:body :game-info :data :enemy])
   '*enemy-info* #(get-in % [:body :game-info :data :enemy :info])
   '*logged-in?* #(= "ok" (get-in % [:body :login :status]))
   '*is-own?* #(get-in % [:body :game-info :data :account :is_own])
   '*is-old?* #(get-in % [:body :game-info :data :account :is_old])
   '*hero-pvp* #(get-in % [:body :game-info :data :account :hero :pvp])
   '*enemy-pvp* #(get-in % [:body :game-info :data :enemy :hero :pvp])
   '*hero-energy* #(get-in % [:body :game-info :data :account :hero :energy])
   '*enemy-energy* #(get-in % [:body :game-info :data :account :enemy :energy])
   '*hero-cards* #(get-in % [:body :game-info :data :account :hero :cards])
   '*hero-companion* #(get-in % [:body :game-info :data :account :hero :companion])
   '*hero-base* #(get-in % [:body :game-info :data :account :hero :base])
   '*enemy-base* #(get-in % [:body :game-info :data :account :enemy :base])
   '*hero-quests* #(get-in % [:body :game-info :data :account :hero :quests])
   '*hero-action* #(get-in % [:body :game-info :data :account :hero :action])
   '*bot-commands* #(keys (:api-settings %))
   })


(defn- prepare-help [session]
  (str "Actions: " (keys (:api-settings session)) "\n"
       "Short access data: " (->> (keys +short-vars+)
                                  (cons '*session*)
                                  (cons '*email*)
                                  (cons '*passwd*))))

(defn- intern-short-vars [sb-ns]
  (map (fn [[v f]] (intern sb-ns v f)) +short-vars+))
  ;(intern sb-ns '*HELP* (prepare-help session)))

;; (defn- prepare-short-vars [session]
;;   (reduce merge (map (fn [[v f]]
;;                        {v (f session)}) +short-vars+)))


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
  (let [nsname 'sandbox
        sb-ns (create-ns nsname)
        curr-ns *ns*]
    (binding [*ns* sb-ns]
      (use '[tt-clj-bot.logic :only (*session*)]))
    (intern sb-ns '*email* email)
    (intern sb-ns '*passwd* passwd)
    (let [sb (jail/sandbox guards/secure-tester :namespace nsname)]
      (fn [session]
        (let [sess (prepare-update-time session)]
          (doseq [[v f] +short-vars+] (intern sb-ns v (f sess)))
          (intern sb-ns '*HELP* (prepare-help sess))
          (try+
           (let [out (java.io.StringWriter.)]
             {:result
              (sb logic-code {#'*session* sess #'*out* out})
              :output
              (str out)})
           (catch java.util.concurrent.TimeoutException _
             (throw+ {:type ::timeout-error :message (:message &throw-context)
                      :session session}))
           (catch Object _
             (throw+ {:type ::execution-error :message (:message &throw-context)
                      :session session}))))))))
         ;; (catch java.util.concurrent.ExecutionException _
         ;;   (throw+ {:type ::execution-error :message (:message &throw-context)}))
         ;; (catch java.lang.SecurityException _
         ;;   (throw+ {:type ::execution-error :message (:message &throw-context)})))))))





