:Title: xmpptrace
:Author: Andrew Biggs <balthorium@gmail.com> 
:Type: Informational 
:Status: Draft
:Revision: $Revision: 18396 $ 
:Last-Modified: $Date: 2010-10-07 13:22:12 -0700 (Thu, 07 Oct 2010) $ 
:Content-Type: text/x-rst

.. contents:: 

.. sectnum::    


What is ``xmpptrace``?
======================

The ``xmpptrace`` application is a visualization and analysis tool for XMPP
protocol traffic.  It reads captured traffic files produced by ``xmppdump`` (or
``tcpdump`` for version 0.95 or later),
and presents the contents in an interactive sequence diagram format.  It
supports filtering of traffic based on specific addresses, ports, and XMPP
actors, providing the user with control over the schematic arrangement
of entities selected for display.  TCP header and XMPP stanza content
of any selected packet may be reviewed, and searched, using either 
regular expressions or XPath queries.  It is best suited for low-traffic
protocol analysis, troubleshooting, and debugging, particularly in cases where
there are multiple XMPP components and clients involved.

    .. figure :: images/screenshot-xmpptrace-1.png
        :scale: 50
        :align: center


How to Build ``xmpptrace``
==========================

If you have Ant installed (version 1.6 or later), ``xmpptrace`` can be built
stand-alone by simply running ``ant`` from the ``xmpptrace`` directory::

    cd xmpptrace
    ant

This will generate an executable jar ``xmpptrace.jar`` in the source root
directory, as well as javadocs rooted at ``doc/index.html``.


Executing ``xmpptrace``
=======================

The ``xmpptrace`` application can be started from the command-line with::

    java -jar xmpptrace.jar

or, to have it immediately load an ``xmppdump`` capture, the name of the
dump file can be supplied at the command line::
    
    java -jar xmpptrace.jar samples/mcast.xml

Alternatively, a ``tcpdump`` trace made with a command like::

    tcpdump -i any -s 0 "port 7400 or port 5222" -w xmpp.pcap

could be viewed in ``xmpptrace`` using the command::

    java -jar xmpptrace.jar xmpp.pcap

Note that on loading a new capture file, ``xmpptrace`` automatically marks
all addresses and actors as not-visible.  You can adjust this by marking the
check-box to the left of each address of interest in the ``Actor Address`` tab
of the lower left panel of the application window.  Also note that, in the
context of this application, an "address" is meant to imply the combined IP
address and TCP port number of a given protocol endpoint.

    .. figure :: images/screenshot-xmpptrace-2.png
        :scale: 100
        :align: center


Automatic Actor Discovery
=========================

When ``xmpptrace`` loads a new capture file, it begins by reading the contents
of the file, and building a table of all IP addresses and transport layer ports
found in the source and destination fields of the TCP packets.  Once this is
complete, it then examines the XMPP protocol within those packets, and attempts
to resolve an "actor" name for each address between which those packets were
passed.  For clients this may be the client jid or user name, and for
components this will be the component id.  When ``xmpptrace`` is able to make
such an association, it will assign that name as the actor to which that
address belongs.  When it cannot make an association, the actor name to which
an address belongs will just be the address string itself.

The extent to which ``xmpptrace`` is able to automatically identify actors in a
given capture is dependent on whether the signature packets it looks for are
present and recognizable.  To identify a client, for example, ``xmpptrace``
looks for packets that are typically found in an authentication exchange.  For
components, it will try to identify actors based on component presence
packets.  If the ``xmppdump`` capture does not include these packets, automatic
actor identification may not occur.  In this case, the user has the option to
perform manual actor assignment.


Manual Actor Assignment
=======================

In cases where a suitable actor could not be automatically discovered for one
or more addresses in the capture, the user has the option to manually assign an
actor to an address.  This is done by going to the ``Actor Address`` tab, and
clicking on the actor cell which you wish to modify.  For example, you may
assign the router multi-accept port to a more descriptive actor name, by
selecting the actor cell of that address row:

    .. figure :: images/screenshot-xmpptrace-3.png
        :scale: 100
        :align: center

and changing the value to "Router".

    .. figure :: images/screenshot-xmpptrace-4.png
        :scale: 100
        :align: center

Editing this table cell will automatically assign the corresponding address to
an actor with the name you provide.  If an actor with that name does not
already exist, one will be created.  If that actor does already exist (i.e.
another address is already assigned to an actor with the same name) then both
addresses will be assigned to the same actor.  

Be aware that packets sent to and from addresses belonging to the same actor
will be drawn from the same actor in the sequence diagram.  So, for example, in
the above capture we would expect packets sent to addresses
``192.168.0.10:5222`` and ``192.168.0.10:33803`` to both terminate on the
same actor line under the actor ``cm-1_jsmcp-1.jabber`` in the scenario
diagram.  


Actor Display Order
===================

After loading a new capture file, and adjusting actor names and visibility as
desired, the user may then adjust the left-to-right order in which actors are
presented in the sequence diagram.  This facility is provided simply to aid in
the understanding of flows.  For example, ``xmpptrace`` may initially present a
select set of actors as:

    .. figure :: images/screenshot-xmpptrace-5.png
        :scale: 50
        :align: center

where it would be more easily understood as:

    .. figure :: images/screenshot-xmpptrace-6.png
        :scale: 50
        :align: center

To make this adjustment, select the ``Actor Display Order`` tab in the lower
left corner of the application window, where you will find a listing of all
actors which are currently visible in the sequence diagram.  Select actors in
the list, and then use the ``Move Up``, ``Move Down``, ``Move to Top``, and
``Move to Bottom`` buttons on that tab to change the order of the actors in
that list.  Actors will then be displayed in the sequence diagram in that same
order, left-to-right.

    .. figure :: images/screenshot-xmpptrace-7.png
        :scale: 100 
        :align: center


Searching
=========

The ``xmpptrace`` application supports searching on captured traffic based
on either a regular expression, or an XPath query.  To perform a regular
expression-based search, select "regex search" from the drop-down list in the
upper left corner of the application window, and enter a regular expression in
the text entry field to the right of it.

    .. figure :: images/screenshot-xmpptrace-8.png
        :scale: 100 
        :align: center

Similarly, an XPath query can be performed by selecting "xpath search" from the
drop-down list, and entering a valid XPath expression in the text entry box.

    .. figure :: images/screenshot-xmpptrace-9.png
        :scale: 100 
        :align: center

After entering a search string, and with the text entry field still in focus,
press enter to execute the query.  The search will begin with the packet
immediately following the currently selected packet, or at the beginning of the
capture if no packet is selected.  Search will continue to the end of the
capture, and will wrap around to the beginning if necessary.  To repeat a
search from the end of the last result, just press enter again.

Note that, for XPath queries, namespaces are included as part of the matching
criteria.  As a convenience, the ``xmpptrace`` application includes prefix
definitions for the full set of protocol namespaces currently defined by the
xmpp.org registrar (http://xmpp.org/registrar/namespaces.xml).  You will find
these listed on the ``XPath Prefixes`` tab.  Custom prefixes for non-standard
namespaces may also be added to the table, by entering them at the first row of
the table (which is intentionally left blank).  For example, a new multicast
namespace may be added to the table:

    .. figure :: images/screenshot-xmpptrace-10.png
        :scale: 100 
        :align: center

And the prefix assigned to it may be subsequently referenced from an XPath
query:

    .. figure :: images/screenshot-xmpptrace-11.png
        :scale: 100 
        :align: center

Tip: After entering a new prefix, the table is automatically re-sorted, so you
may have to scroll down to see the prefix mapping you have just entered.

Release Notes
=============

Release 0.90
------------

Inaugural release, all features covered in the notes above.

Release 0.95
------------

* Updated look-and-feel: more "Open Office", less "Space Invaders".
* File menu ``Import`` option now allows for the import of either ``xmppdump`` or
  ``tcpdump`` (-w option) produced files.  Note that ``xmppdump`` files are
  expected to be suffixed with ``.xml``, and ``tcpdump`` files are expected to
  be suffixed with ``.pcap``.  Note also that importing another file will merge
  packets from the imported file with those already included in the current
  view (based on timestamp).  This can be helpful when constructing a view that 
  includes traces captured from multiple servers.  As before, a single filename
  can be included at the command-line to request import at startup (file may be
  ``xmppdump`` or ``tcpdump`` produced). 
* File menu ``Save As`` option now allows the user to save the complete state of
  the current view (including actor renames, selection, and viewing order) to
  an H2 database file.  The file menu "Open" option provides a means for 
  opening previously saved views.  Note that unlike "Import", "Open" does not
  merge the new file data with the current view, but rather discards the 
  current view data altogether.
* File menu ``New`` option clears the current view of all data.  Useful if you
  wish to import a new ``xmppdump`` or ``tcpdump`` file, but do not wish to
  have it merged with the current view.
* File menu ``Reduce`` option discards all packets and actors which are not
  currently selected for viewing.  This is useful for paring down the data
  set for easier viewing, or for efficiency right before saving the view as
  an H2 database.

Release 0.96
------------

* Fixed bug in xmpp document viewer (stanza pane) where malformed xml, 
  resulting from stanzas spanning multiple ip packets, was not indenting
  correctly and some text was being repeated.

.. vim:set syntax=rest:

