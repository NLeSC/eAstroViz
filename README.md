This is a visualization and RFI mitigation tool of the Netherlands eScience center eAstronomy project.

This tool can convert and visualize radio astronomy measurement sets
(i.e. visibilities), as well as most LOFAR intermediate data products,
such as raw voltages, filtered data and beam formed data. In addition,
this tool can perform RFI mitigation.

Please cite as:

Rob V. van Nieuwpoort and the LOFAR team:
Exascale Real-Time Radio Frequency Interference Mitigation
Exascale Radio Astronomy, AAS Topical Conference Series
Vol. 2. Proceedings of the conference held 30 March - 4 April, 2014 in
Monterey, California. Bulletin of the American Astronomical Society,
Vol. 46, #3, #403.01

The slides used at the conference are available in the doc directory
in this package, and are available online here:

http://rvannieuwpoort.synology.me/papers/Exascale-Astronomy-2014-Monterey-RFI.pdf

The abstract of this article is here: 

http://adsabs.harvard.edu/abs/2014era..conf40301V

The abstract is shown below as well:



Exascale Real-Time Radio Frequency Interference Mitigation
----------------------------------------------------------

Radio Frequency Interference (RFI) mitigation is extremely important
to take advantage of the vastly improved bandwidth, sensitivity, and
field-of-view of exascale telescopes. For current instruments, RFI
mitigation is typically done offline, and in some cases (partially)
manually. At the same time, it is clear that due to the high bandwidth
requirements, RFI mitigation will have to be done automatically, and
in real-time, for exascale instruments. 

In general, real-time RFI
mitigation will be less precise than offline approaches. Due to memory
constraints, there is much less data to work with, typically only in
the order of one second or less, as opposed to the entire
observation. In addition, we can record only limited statistics of the
past. Moreover, we will typically have only few frequency channels
locally available at each compute core. Finally, the amount of
processing that can be spent on RFI mitigation is extremely limited
due to computing and power constraints. Nevertheless, there are
potential benefits as well, which include the possibility of working
on higher time and frequency resolutions before any integration is
done, leading to more accurate results. Most importantly, we can
remove RFI before beam forming, which combines data from all
receivers. The RFI that is present in the data streams from the
separate receivers is also combined, effectively taking the union of
all RFI. Thus, the RFI from all receivers pollutes all
beams. Therefore, it is essential to do real-time RFI mitigation
before the beam former. This is particularly important for pulsar
surveys, for instance. modes. 

Although our techniques are generic, we
describe how we implemented real-time RFI mitigation for one of the
SKA pathfinders: The Low Frequency Array (LOFAR). The RFI mitigation
algorithms and operations we introduce here are extremely fast, and
the computational requirements scale linearly in the number of samples
and frequency channels. We evaluate the quality of the algorithms with
real LOFAR pulsar observations. By comparing the signal-to-noise
ratios of the folded pulse profiles, we can quantitatively compare the
impact of real-time RFI mitigation, and compare different algorithms.


Copyright 2016 The Netherlands eScience Center

Written by Rob van Nieuwpoort, R.vanNieuwpoort@esciencecenter.nl
