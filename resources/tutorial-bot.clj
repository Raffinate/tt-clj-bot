(let [game-info (-> *session*
                       :body :game-info)
      account-info (-> game-info
                       :data :account)
      hero-base-info (-> account-info
                         :hero :base)
      energy (-> account-info
                 :hero :energy)
      action (-> account-info
                 :hero :action)

      cards-data (-> account-info
                          :hero :cards)

      old-info? (-> account-info
                    :is_old true?)
      own-info? (-> account-info
                    :is_own true?)
      logged-in? (-> *session*
                     :body :login :status (= "ok"))

      hp (:health hero-base-info)
      max-hp (:max_health hero-base-info)
      alive? (:alive hero-base-info)
      hpp (if (integer? max-hp) (/ hp max-hp) 1)


      current-energy (:value energy)
      max-energy (:max energy)
      bonus-energy (:bonus energy)


      action-type (:type action)
      action-percents (:percents action)
      action-boss? (:is_boss action)
      action-kind (cond
                    (contains? #{0 4 5 6 7 8 10 15} action-type) :safe
                    (= action-type 3) :fight
                    (contains? #{1 2 3 9} action-type) :danger
                    :else :default)
      help-action-cost (-> *session*
                           :body :api-info :data :abilities_cost :help)

      card-help-count (:help_count cards-data)
      card-help-barrier (:help_barrier cards-data)
      get-card? (when (and (integer? card-help-count)
                           (integer? card-help-barrier)
                           (= card-help-barrier card-help-count))
                  true)

      cards (:cards cards-data)
      merge-cards (->> cards
                       (filter #(< (:rarity %) 4))
                       (partition-by :rarity)
                       (filter #(>= (count %) 3))
                       (map (fn [same-rarity-cards]
                              (mapv :uid same-rarity-cards)))
                       (#(when (not (empty? %)) %))
                       first)


      login-action [:login *email* *passwd*]
      info-action [:game-info]
      api-info-action [:api-info]
      help-action [:use-ability :help]
      get-card-action [:get-card]
      merge-cards-action [:combine-cards] ;don't forget to add the cards

      base-wait-time (int (+ (* 275 hpp hpp) (* 85 hpp)))
      safe-wait-time 360
      boss-wait-time (int (/ base-wait-time 2))
      wait-time (if action-boss? boss-wait-time base-wait-time)
      ability-wait-time 5

      hpp-help-default 0.1
      fight-percent-to-help 0.8
      hpp-help-critical 0.05
      safe-pause-percent 0.5
      enough-energy? (if (and (integer? current-energy)
                              (integer? help-action-cost))
                       (>= current-energy  help-action-cost)
                       false)

      combine-cards ()
      ]
  (println "")
  (println *session*)
  (println "Hero base info: " hero-base-info)
  (println "Energy: " energy)
  (println "Action: " action "(" action-kind ")")
  (println "Cards: " card-help-count "/" card-help-barrier)

  (cond
    (not *session*) (do (print "Logging in...")
                        [login-action api-info-action])

    (not logged-in?) (do (println "Couldn't log in...")
                         (print "Logging again in 30 seconds...")
                         [30 login-action])

    (not game-info) (do (println "Requesting game info first time...")
                           [info-action])

    (not own-info?) (do (println "Game info is NOT about own character.")
                        (print "Logging again in 30 seconds...")
                        [30 login-action])

    old-info? (do (print "Game info is old!")
                  [5 info-action])

    (and (= action-kind :fight) (< hpp hpp-help-critical) enough-energy?)
    (do (print "Critical situaltion...")
        [help-action info-action])

    (and (= action-kind :fight)
         (< action-percents fight-percent-to-help)
         (< hpp hpp-help-default)
         enough-energy?)
    (do (print "Helping hero...")
        [help-action ability-wait-time info-action])

    (true? get-card?) (do (print "Whoaaa... A new destiny card!")
                          [get-card-action ability-wait-time info-action])

    merge-cards (do (print "Merging cards to get the best! It would be sad to break them.")
                    [(concat merge-cards-action merge-cards)
                    ability-wait-time
                    info-action])

    (and (= action-kind :safe) (< action-percents safe-pause-percent))
    (do (print "It's nice to be safe...")
        [safe-wait-time info-action])

    (and (= action-kind :fight) (= current-energy max-energy) enough-energy?)
    (do (print "Gonna get more card points! Helping...")
        [help-action wait-time info-action])

    :else
    (do (print "Boring...")
        [wait-time info-action])))
