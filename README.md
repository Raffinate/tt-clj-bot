# tt-clj-bot

This is a small bot for http://the-tale.org
I wrote it to practice clojure.

## Installation

Copy latest uberjar to the directory. Create "resource" folder.
Place there "java.security" file. You can take it from this repo.
Create an account file with "{:email "email@emailwebsite.com" :password "pswrd"}"
Create a source file with bot logic. There is a tutorial bot in this repo.

## Usage

   Run

    $ java -jar tt-clj-bot-0.1.0-standalone.jar <bot source> <account source>

## Examples

   Bot logic can be written in clojure and it will be evaluted in restricted sandbox.
   It should be a single form, like (let [] ... ) or (do ... ).
   This form should return a vector af actions. Action will be made
   in the order.
   Action is a vector [:action-name param1 param2 ...] or positive integer.
   If action is integer n, the execution will pause for the n seconds.
   If action is [:action-name param1 param2 ...] then the request to the
   game api will be performed.
   If you 'print' something in the bot logic file, then this message will
   be printed in log file and terminal.

   There are 3 globals: *session* *email* *passwd*.
   *session* - is a map where the server responses are stored.
               It may be nil at the beginning. Different api responses
               are stored in different keys.
               To see how *session* is made,
               you may want to create this bot:
               (do
                 (print "Session: " *session*)
                 [[login *email* *passwd*]
                 [:game-info]])

### Bugs

## License

Copyright © 2015 Raffinate

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
